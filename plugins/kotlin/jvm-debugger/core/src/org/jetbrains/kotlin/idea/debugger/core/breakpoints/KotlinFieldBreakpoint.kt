// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.requests.Requestor
import com.intellij.debugger.ui.breakpoints.BreakpointCategory
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter
import com.intellij.debugger.ui.breakpoints.FieldBreakpoint
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.*
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.MethodEntryRequest
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.debugger.base.util.safeAllLineLocations
import org.jetbrains.kotlin.idea.debugger.base.util.runSmartAnalyze
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import javax.swing.Icon

class KotlinFieldBreakpoint(
    project: Project,
    breakpoint: XBreakpoint<KotlinPropertyBreakpointProperties>
) : BreakpointWithHighlighter<KotlinPropertyBreakpointProperties>(project, breakpoint) {
    companion object {
        private val LOG = Logger.getInstance(KotlinFieldBreakpoint::class.java)
        private val CATEGORY: Key<FieldBreakpoint> = BreakpointCategory.lookup("field_breakpoints")
    }

    private enum class BreakpointType {
        FIELD,
        METHOD
    }

    private var breakpointType: BreakpointType = BreakpointType.FIELD

    override fun isValid(): Boolean {
        return super.isValid() && evaluationElement != null
    }

    @RequiresBackgroundThread
    override fun reload() {
        super.reload()

        val callable = evaluationElement ?: return
        val callableName = callable.name ?: return
        setFieldName(callableName)

        if (callable is KtProperty && callable.isTopLevel) {
            properties.myClassName = JvmFileClassUtil.getFileClassInfoNoResolve(callable.getContainingKtFile()).fileClassFqName.asString()
        } else {
            val ktClass: KtClassOrObject? = PsiTreeUtil.getParentOfType(callable, KtClassOrObject::class.java)
            if (ktClass is KtClassOrObject) {
                val fqName = ktClass.fqName
                if (fqName != null) {
                    properties.myClassName = fqName.asString()
                }
            }
        }
        isInstanceFiltersEnabled = false
    }

    override fun createRequestForPreparedClass(debugProcess: DebugProcessImpl?, refType: ReferenceType?) {
        if (debugProcess == null || refType == null) return

        breakpointType = evaluationElement?.let(::computeBreakpointType) ?: return

        val vm = debugProcess.virtualMachineProxy
        try {
            if (properties.watchInitialization) {
                val sourcePosition = sourcePosition
                if (sourcePosition != null) {
                    debugProcess.positionManager
                        .locationsOfLine(refType, sourcePosition)
                        .filter { it.method().isConstructor || it.method().isStaticInitializer }
                        .forEach {
                            val request = debugProcess.requestsManager.createBreakpointRequest(this, it)
                            debugProcess.requestsManager.enableRequest(request)
                            if (LOG.isDebugEnabled) {
                                LOG.debug("Breakpoint request added")
                            }
                        }
                }
            }

            when (breakpointType) {
                BreakpointType.FIELD -> {
                    val field = refType.fieldByName(getFieldName())
                    if (field != null) {
                        val manager = debugProcess.requestsManager
                        if (properties.watchModification && vm.canWatchFieldModification()) {
                            val request = manager.createModificationWatchpointRequest(this, field)
                            debugProcess.requestsManager.enableRequest(request)
                            if (LOG.isDebugEnabled) {
                                LOG.debug("Modification request added")
                            }
                        }
                        if (properties.watchAccess && vm.canWatchFieldAccess()) {
                            val request = manager.createAccessWatchpointRequest(this, field)
                            debugProcess.requestsManager.enableRequest(request)
                            if (LOG.isDebugEnabled) {
                                LOG.debug("Field access request added (field = ${field.name()}; refType = ${refType.name()})")
                            }
                        }
                    }
                }
                BreakpointType.METHOD -> {
                    val fieldName = getFieldName()

                    if (properties.watchAccess) {
                        val getter = refType.methodsByName(JvmAbi.getterName(fieldName)).firstOrNull()
                        if (getter != null) {
                            createMethodBreakpoint(debugProcess, refType, getter)
                        }
                    }

                    if (properties.watchModification) {
                        val setter = refType.methodsByName(JvmAbi.setterName(fieldName)).firstOrNull()
                        if (setter != null) {
                            createMethodBreakpoint(debugProcess, refType, setter)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            LOG.debug(ex)
        }
    }

    private fun computeBreakpointType(property: KtCallableDeclaration): BreakpointType {
        return runSmartAnalyze(property) {
            val hasBackingField = when (val symbol = property.symbol) {
                is KaValueParameterSymbol -> symbol.generatedPrimaryConstructorProperty?.hasBackingField ?: false
                is KaKotlinPropertySymbol -> symbol.hasBackingField
                else -> false
            }

            if (hasBackingField) BreakpointType.FIELD else BreakpointType.METHOD
        }
    }

    private fun createMethodBreakpoint(debugProcess: DebugProcessImpl, refType: ReferenceType, accessor: Method) {
        val manager = debugProcess.requestsManager
        val line = accessor.safeAllLineLocations().firstOrNull()
        if (line != null) {
            val request = manager.createBreakpointRequest(this, line)
            debugProcess.requestsManager.enableRequest(request)
            if (LOG.isDebugEnabled) {
                LOG.debug("Breakpoint request added")
            }
        } else {
            var entryRequest: MethodEntryRequest? = findRequest(debugProcess, MethodEntryRequest::class.java, this)
            if (entryRequest == null) {
                entryRequest = manager.createMethodEntryRequest(this)!!
                if (LOG.isDebugEnabled) {
                    LOG.debug("Method entry request added (method = ${accessor.name()}; refType = ${refType.name()})")
                }
            } else {
                entryRequest.disable()
            }
            entryRequest.addClassFilter(refType)
            manager.enableRequest(entryRequest)
        }
    }

    private inline fun <reified T : EventRequest> findRequest(
        debugProcess: DebugProcessImpl,
        requestClass: Class<T>,
        requestor: Requestor
    ): T? {
        val requests = debugProcess.requestsManager.findRequests(requestor)
        for (eventRequest in requests) {
            if (eventRequest::class.java == requestClass) {
                return eventRequest as T
            }
        }
        return null
    }

    override fun evaluateCondition(context: EvaluationContextImpl, event: LocatableEvent): Boolean {
        if (breakpointType == BreakpointType.METHOD && !matchesEvent(event)) {
            return false
        }
        return super.evaluateCondition(context, event)
    }

    private fun matchesEvent(event: LocatableEvent): Boolean {
        val method = event.location()?.method()
        // TODO check property type
        return method != null && method.name() in getMethodsName()
    }

    private fun getMethodsName(): List<String> {
        val fieldName = getFieldName()
        return listOf(JvmAbi.getterName(fieldName), JvmAbi.setterName(fieldName))
    }

    override fun getEventMessage(event: LocatableEvent): String {
        val location = event.location()!!
        val locationQName = location.declaringType().name() + "." + location.method().name()
        val locationFileName = try {
            location.sourceName()
        } catch (e: AbsentInformationException) {
            fileName
        } catch (e: InternalError) {
            fileName
        }

        val locationLine = location.lineNumber()
        when (event) {
            is ModificationWatchpointEvent -> {
                val field = event.field()
                return JavaDebuggerBundle.message(
                    "status.static.field.watchpoint.reached.access",
                    field.declaringType().name(),
                    field.name(),
                    locationQName,
                    locationFileName,
                    locationLine
                )
            }
            is AccessWatchpointEvent -> {
                val field = event.field()
                return JavaDebuggerBundle.message(
                    "status.static.field.watchpoint.reached.access",
                    field.declaringType().name(),
                    field.name(),
                    locationQName,
                    locationFileName,
                    locationLine
                )
            }
            is MethodEntryEvent -> {
                val method = event.method()
                return JavaDebuggerBundle.message(
                    "status.method.entry.breakpoint.reached",
                    method.declaringType().name() + "." + method.name() + "()",
                    locationQName,
                    locationFileName,
                    locationLine
                )
            }
            is MethodExitEvent -> {
                val method = event.method()
                return JavaDebuggerBundle.message(
                    "status.method.exit.breakpoint.reached",
                    method.declaringType().name() + "." + method.name() + "()",
                    locationQName,
                    locationFileName,
                    locationLine
                )
            }
        }
        return JavaDebuggerBundle.message(
            "status.line.breakpoint.reached",
            locationQName,
            locationFileName,
            locationLine
        )
    }

    fun setFieldName(fieldName: String) {
        properties.myFieldName = fieldName
    }

    @TestOnly
    fun setWatchAccess(value: Boolean) {
        properties.watchAccess = value
    }

    @TestOnly
    fun setWatchModification(value: Boolean) {
        properties.watchModification = value
    }

    @TestOnly
    fun setWatchInitialization(value: Boolean) {
        properties.watchInitialization = value
    }

    override fun getDisabledIcon(isMuted: Boolean): Icon {
        val master = DebuggerManagerEx.getInstanceEx(myProject).breakpointManager.findMasterBreakpoint(this)
        return when {
            isMuted && master == null -> AllIcons.Debugger.Db_muted_disabled_field_breakpoint
            isMuted && master != null -> AllIcons.Debugger.Db_muted_dep_field_breakpoint
            master != null -> AllIcons.Debugger.Db_dep_field_breakpoint
            else -> AllIcons.Debugger.Db_disabled_field_breakpoint
        }
    }

    override fun getSetIcon(isMuted: Boolean): Icon {
        return when {
            isMuted -> AllIcons.Debugger.Db_muted_field_breakpoint
            else -> AllIcons.Debugger.Db_field_breakpoint
        }
    }

    override fun getVerifiedIcon(isMuted: Boolean): Icon {
        return when {
            isMuted -> AllIcons.Debugger.Db_muted_field_breakpoint
            else -> AllIcons.Debugger.Db_verified_field_breakpoint
        }
    }

    override fun getVerifiedWarningsIcon(isMuted: Boolean): Icon = AllIcons.Debugger.Db_exception_breakpoint

    override fun getCategory() = CATEGORY

    override fun getDisplayName(): String {
        if (!isValid) {
            return JavaDebuggerBundle.message("status.breakpoint.invalid")
        }
        val className = className
        @Suppress("HardCodedStringLiteral")
        return if (!className.isNullOrEmpty()) className + "." + getFieldName() else getFieldName()
    }

    private fun getFieldName(): String {
        return runReadAction {
            evaluationElement?.name ?: "unknown"
        }
    }

    override fun getEvaluationElement(): KtCallableDeclaration? {
        return when (val callable = PositionUtil.getPsiElementAt(project, KtValVarKeywordOwner::class.java, sourcePosition)) {
            is KtProperty -> callable
            is KtParameter -> callable
            else -> null
        }
    }
}
