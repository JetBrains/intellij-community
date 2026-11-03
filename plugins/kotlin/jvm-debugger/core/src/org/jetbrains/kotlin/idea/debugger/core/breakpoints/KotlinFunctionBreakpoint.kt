// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint.MethodDescriptor
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.util.DocumentUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.sun.jdi.IntegerValue
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodEntryEvent
import com.sun.jdi.event.MethodExitEvent
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.scopes.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.codegen.coroutines.SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
import org.jetbrains.kotlin.idea.debugger.base.util.runSmartReadActionIfUnderProgressElseDumb
import org.jetbrains.kotlin.idea.debugger.base.util.safeVisibleVariableByName
import org.jetbrains.kotlin.idea.debugger.core.ClassNameProvider
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerLegacyFacade
import org.jetbrains.kotlin.idea.debugger.core.isInSuspendMethod
import org.jetbrains.kotlin.idea.debugger.core.methodName
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.getJvmSignature
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

// This is an enum class containing singletons for the state of the coroutine.
// Coroutines return `CoroutineSingletons.COROUTINE_SUSPENDED` from their state machine when they suspend.
private const val COROUTINE_SINGLETONS_CLASS_NAME = "kotlin.coroutines.intrinsics.CoroutineSingletons"
// The name of the field that holds the state of the state machine of a coroutine.
private const val CONTINUATION_LABEL_FIELD_NAME = "label"

interface SourcePositionRefiner {
    fun refineSourcePosition(sourcePosition: SourcePosition): SourcePosition
}

open class KotlinFunctionBreakpoint(
    project: Project,
    breakpoint: XBreakpoint<*>
) : MethodBreakpoint(project, breakpoint), SourcePositionRefiner {
    override fun getPsiClass(): PsiClass? {
        val declaration = getKtClass() ?: return null
        return runSmartReadActionIfUnderProgressElseDumb(myProject, null) {
            declaration.toLightClass()
        }
    }

    override fun evaluateCondition(context: EvaluationContextImpl, event: LocatableEvent): Boolean {
        if (!matchesEvent(event, context.debugProcess)) return false
        if (resumesOrSuspendsCoroutine(context, event)) return false
        return super.evaluateCondition(context, event)
    }

    /**
     * Returns true iff the [event] suspends a running coroutine or resumes a previously suspended coroutine.
     * Within the coroutine machinery these function calls count as method entries and exits, but we do
     * not want them to trigger method breakpoints.
     *
     * Note: Tail call optimization by the Kotlin compiler can remove the state machine.
     * In such a case, no exit event will be captured after suspension because the result goes directly to the caller.
     * Note: When using emulated exit breakpoints, there is no return value when `debugger.emulated.method.return.values` is false.
     * In that case each suspension will still show as an exit event.
     */
    private fun resumesOrSuspendsCoroutine(context: EvaluationContextImpl, event: LocatableEvent): Boolean {
        val location = event.location()
        if (!isInSuspendMethod(location)) return false

        if (event is MethodExitEvent) {
            if (!event.virtualMachine().canGetMethodReturnValues()) return false
            val returnValue = event.returnValue() as? ObjectReference ?: return false
            // The singleton is returned by the coroutines machinery whenever the coroutine suspends
            return returnValue.referenceType().name() == COROUTINE_SINGLETONS_CLASS_NAME
        }

        val isEntry = event is MethodEntryEvent || event.request().getProperty(METHOD_ENTRY_KEY) == true
        if (!isEntry) return false
        return resumesCoroutine(context, location)
    }

    /**
     * Returns whether the [location] resumes a coroutine after it was suspended before.
     */
    private fun resumesCoroutine(context: EvaluationContextImpl, location: Location): Boolean {
        val frame = context.frameProxy ?: return false
        // In case of any JDI failure/exception, we fall back to the default behavior.
        try {
            // Internally, the last parameter when calling/resuming a coroutine is the reference to the continuation
            val continuation = frame.continuationArgument() ?: return false
            val continuationType = continuation.referenceType()
            // The continuation type is given a generated name with some structure we can check for
            if (!continuationType.name().startsWith(location.declaringType().name() + "$")) return false

            // When resuming the coroutine, the label field is used to decide which branch in the state machine to use.
            // Additionally, the label also has the most significant bit set when the coroutine is being resumed rather than
            // called for the first time.
            val labelField = DebuggerUtils.findField(continuationType, CONTINUATION_LABEL_FIELD_NAME) ?: return false
            val label = (continuation.getValue(labelField) as? IntegerValue)?.value() ?: return false
            return label and Int.MIN_VALUE != 0
        }
        catch (e: Throwable) {
            rethrowControlFlowException(e)
            thisLogger().debug("Cannot check if the entry at $location resumes a coroutine", e)
            return false
        }
    }

    /**
     * Returns the continuation argument of the coroutine in this frame.
     * This is needed because it is different for Android than it is for the JVM.
     */
    private fun StackFrameProxyImpl.continuationArgument(): ObjectReference? {
        if (DebuggerUtils.isAndroidVM(virtualMachine.virtualMachine)) {
            val variable = safeVisibleVariableByName(SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME) ?: return null
            return getValue(variable) as? ObjectReference
        }
        return argumentValues.lastOrNull() as? ObjectReference
    }

    override fun refineSourcePosition(sourcePosition: SourcePosition): SourcePosition {
        val declaration = sourcePosition.elementAt?.parentOfType<KtDeclaration>(withSelf = true) ?: return sourcePosition
        if (declaration.isExpectDeclaration()) {
            val actualDeclaration = declaration.getActualJvmDeclaration() ?: return sourcePosition
            return SourcePosition.createFromElement(actualDeclaration) ?: sourcePosition
        }

        return sourcePosition
    }

    override fun computeMethodDescriptor(sourcePosition: SourcePosition): MethodBreakpoint.MethodDescriptor? {
        ProgressManager.checkCanceled()
        return sourcePosition.getMethodDescriptor(myProject)
    }

    override fun computeClassPattern(): String? {
        val declaration = getKtClass() ?: return null
        val pattern = ClassNameProvider().getCandidatesForElement(declaration).firstOrNull()
        if (pattern != null) return pattern
        // fallback to java
        return psiClass?.qualifiedName
    }

    private fun getKtClass(): KtClassOrObject? {
        val sourcePosition = sourcePosition
        return PositionUtil.getPsiElementAt(myProject, KtClassOrObject::class.java, sourcePosition)
    }
}

fun SourcePosition.getMethodDescriptor(project: Project): MethodDescriptor? =
    runSmartReadActionIfUnderProgressElseDumb(project, null) f@{
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@f null
        val descriptor = getMethodDescriptorInReadActionInSmartMode(project, this, document)
        descriptor?.takeIf { it.methodName != null && it.methodSignature != null }
    }

private fun getMethodDescriptorInReadActionInSmartMode(
    project: Project,
    sourcePosition: SourcePosition,
    document: Document
): MethodDescriptor? {
    val declaration = findDeclaration(project, sourcePosition) ?: return null
    createMethodDescriptor(declaration, sourcePosition, document)?.let { return it }

    // fallback to java way signature extraction
    val psiMethod = declaration.toLightElements().firstIsInstanceOrNull<PsiMethod>() ?: return null
    return MethodBreakpoint.getMethodDescriptor(sourcePosition, psiMethod, document)
}

private fun findDeclaration(project: Project, sourcePosition: SourcePosition): KtDeclaration? {
    var declaration = PositionUtil.getPsiElementAt(project, KtDeclaration::class.java, sourcePosition)
        ?: return null
    if (declaration.isExpectDeclaration()) {
        declaration = declaration.getActualJvmDeclaration() ?: return null
    }

    return declaration
}

private fun isValidDeclaration(declaration: KtDeclaration, sourcePosition: SourcePosition, document: Document): Boolean {
    val offset = declaration.textOffset
    return DocumentUtil.isValidOffset(offset, document) && document.getLineNumber(offset) >= sourcePosition.line
}

private fun createMethodDescriptor(declaration: KtDeclaration, sourcePosition: SourcePosition, document: Document): MethodDescriptor? {
    if (!isValidDeclaration(declaration, sourcePosition, document)) return null
    return analyze(declaration) { createMethodDescriptor(document, declaration) }
}

context(session: KaSession)
private fun createMethodDescriptor(document: Document, declaration: KtDeclaration): MethodDescriptor? {
    var symbol = declaration.symbol
    val lineNumber = document.getLineNumber(declaration.textOffset)
    if (symbol is KaNamedClassSymbol) {
        symbol = symbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary } ?: return null
    }
    if (symbol !is KaFunctionSymbol) return null
    val jvmSignature = getJvmSignature(symbol, isConstructor = symbol is KaConstructorSymbol) ?: return null
    val methodName = methodName(symbol) ?: return null
    return MethodDescriptor(methodName, jvmSignature, false, lineNumber)
}

private fun KtDeclaration.getActualJvmDeclaration(): KtDeclaration? =
    KotlinDebuggerLegacyFacade.getInstance()
        ?.actualDeclarationProvider
        ?.getActualJvmDeclaration(this)
