// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint.MethodDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.debugger.base.util.runSmartReadActionIfUnderProgressElseDumb
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle.message
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerLegacyFacade
import org.jetbrains.kotlin.idea.debugger.core.methodName
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.getJvmSignature
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

interface SourcePositionRefiner {
    fun refineSourcePosition(sourcePosition: SourcePosition): SourcePosition
}

open class KotlinFunctionBreakpoint(
    project: Project,
    breakpoint: XBreakpoint<*>
) : MethodBreakpoint(project, breakpoint), SourcePositionRefiner {
    override fun getPsiClass(): PsiClass? {
        val sourcePosition = sourcePosition
        val declaration = PositionUtil.getPsiElementAt(
            myProject,
            KtClassOrObject::class.java,
            sourcePosition
        ) ?: return null

        return runSmartReadActionIfUnderProgressElseDumb(myProject, null) {
            declaration.toLightClass()
        }
    }

    override fun refineSourcePosition(sourcePosition: SourcePosition): SourcePosition {
        val declaration = sourcePosition.elementAt?.parentOfType<KtDeclaration>(withSelf = true) ?: return sourcePosition
        if (declaration.isExpectDeclaration()) {
            val actualDeclaration = declaration.getActualJvmDeclaration() ?: return sourcePosition
            return SourcePosition.createFromElement(actualDeclaration) ?: sourcePosition
        }

        return sourcePosition
    }

    @RequiresBackgroundThread
    override fun reload() {
        super.reload()

        // We can't wait for a smart mode under a read access. It would result to exceptions
        // or a possible deadlock. So return here.
        if (ApplicationManager.getApplication().isReadAccessAllowed && DumbService.isDumb(myProject)) {
            return
        }

        invalidateMethodData()
        val descriptor = sourcePosition?.getMethodDescriptor(myProject)
        ProgressIndicatorProvider.checkCanceled()
        updateMethodData(descriptor)
        updateClassPattern()
    }

    private fun invalidateMethodData() {
        methodName = null
        mySignature = null
    }

    private fun updateMethodData(descriptor: MethodDescriptor?) {
        methodName = descriptor?.methodName
        mySignature = descriptor?.methodSignature
        if (descriptor != null && descriptor.isStatic) {
            isInstanceFiltersEnabled = false
        }
    }

    private fun updateClassPattern() {
        ProgressIndicatorProvider.checkCanceled()
        val task = object : Backgroundable(myProject, message("function.breakpoint.initialize")) {
            override fun run(indicator: ProgressIndicator) {
                val psiClass = psiClass
                if (psiClass != null) {
                    properties.myClassPattern = runReadAction { psiClass.qualifiedName }
                }
            }
        }
        val progressManager = ProgressManager.getInstance()
        if (isDispatchThread() && !isUnitTestMode()) {
            progressManager.runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
        } else {
            val progressIndicator = EmptyProgressIndicator()
            progressManager.runProcess({ task.run(progressIndicator) }, progressIndicator)
        }
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

@OptIn(KaExperimentalApi::class)
private fun KaSession.createMethodDescriptor(document: Document, declaration: KtDeclaration): MethodDescriptor? {
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
