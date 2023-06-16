// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint.MethodDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.parentOfType
import com.intellij.util.DocumentUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle.message
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerLegacyFacade
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

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

        return DumbService.getInstance(myProject).runReadActionInSmartMode<KtLightClass?> {
            declaration.toLightClass()
        }
    }

    override fun refineSourcePosition(sourcePosition: SourcePosition): SourcePosition {
        val declaration = sourcePosition.elementAt.parentOfType<KtDeclaration>(withSelf = true) ?: return sourcePosition
        if (declaration.isExpectDeclaration()) {
            val actualDeclaration = declaration.getActualJvmDeclaration() ?: return sourcePosition
            return SourcePosition.createFromElement(actualDeclaration) ?: sourcePosition
        }

        return sourcePosition
    }

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
        setMethodName(null)
        mySignature = null
    }

    private fun updateMethodData(descriptor: MethodDescriptor?) {
        setMethodName(descriptor?.methodName)
        mySignature = descriptor?.methodSignature
        myIsStatic = descriptor != null && descriptor.isStatic
        if (myIsStatic) {
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
    DumbService.getInstance(project).runReadActionInSmartMode<MethodDescriptor?> {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runReadActionInSmartMode null
        val descriptor = getMethodDescriptorInReadActionInSmartMode(project, this, document)
        descriptor?.takeIf { it.methodName != null && it.methodSignature != null }
    }

private fun getMethodDescriptorInReadActionInSmartMode(
    project: Project,
    sourcePosition: SourcePosition,
    document: Document
): MethodDescriptor? {
    val method = resolveJvmMethodFromKotlinDeclaration(project, sourcePosition) ?: return null
    if (!method.hasAppropriateKotlinOrigin(sourcePosition, document)) {
        return null
    }

    return method.getMethodDescriptor()
}

private fun PsiMethod.hasAppropriateKotlinOrigin(sourcePosition: SourcePosition, document: Document): Boolean {
    val kotlinOrigin = this.safeAs<KtLightMethod>()?.getSourceOrigin() ?: return true
    val offset = kotlinOrigin.textOffset
    return DocumentUtil.isValidOffset(offset, document) && document.getLineNumber(offset) >= sourcePosition.line
}

private fun PsiMethod.getMethodDescriptor(): MethodDescriptor? =
    try {
        val descriptor = MethodDescriptor()
        descriptor.methodName = JVMNameUtil.getJVMMethodName(this)
        descriptor.methodSignature = JVMNameUtil.getJVMSignature(this)
        descriptor.isStatic = hasModifierProperty(PsiModifier.STATIC)
        descriptor
    } catch (ignored: IndexNotReadyException) {
        null
    }

private fun KtLightMethod.getSourceOrigin(): KtDeclaration? {
    val lightMemberOrigin = lightMemberOrigin ?: return null
    val originalElement = lightMemberOrigin.auxiliaryOriginalElement
        ?: lightMemberOrigin.originalElement
        ?: return null
    val sourceOrigin = originalElement.fetchNavigationElement() as? KtDeclaration ?: return null
    if (sourceOrigin.containingFile is KtClsFile) {
        return null
    }

    return sourceOrigin
}

private fun resolveJvmMethodFromKotlinDeclaration(project: Project, sourcePosition: SourcePosition): PsiMethod? {
    var declaration = PositionUtil.getPsiElementAt(project, KtDeclaration::class.java, sourcePosition) ?: return null
    if (declaration.isExpectDeclaration()) {
        declaration = declaration.getActualJvmDeclaration() ?: return null
    }

    if (declaration is KtClass) {
        val constructor = declaration.primaryConstructor
        if (constructor == null) {
            val lightClass = declaration.safeAs<KtClassOrObject>()?.toLightClass()
            return lightClass?.constructors?.firstOrNull()
        }
        declaration = constructor
    }

    val originalDeclaration = declaration.fetchOriginalElement() ?: return null
    return originalDeclaration.toLightElements().firstIsInstanceOrNull()
}

private fun KtDeclaration.getActualJvmDeclaration(): KtDeclaration? =
    KotlinDebuggerLegacyFacade.getInstance()
        ?.actualDeclarationProvider
        ?.getActualJvmDeclaration(this)

private fun KtDeclaration.fetchNavigationElement(): KtElement? =
    serviceOrNull<KotlinDeclarationNavigationPolicy>()?.getNavigationElement(this)

private fun KtDeclaration.fetchOriginalElement(): KtElement? =
    serviceOrNull<KotlinDeclarationNavigationPolicy>()?.getOriginalElement(this)
