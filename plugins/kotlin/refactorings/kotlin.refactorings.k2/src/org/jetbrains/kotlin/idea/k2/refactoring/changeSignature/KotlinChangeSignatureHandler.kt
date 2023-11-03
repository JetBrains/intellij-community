// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui.KotlinChangePropertySignatureDialog
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui.KotlinChangeSignatureDialog
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureHandlerBase
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions

object KotlinChangeSignatureHandler : KotlinChangeSignatureHandlerBase<KtNamedDeclaration>() {
    override fun asInvokeOperator(call: KtCallElement?): PsiElement? {
        val psiElement = call?.mainReference?.resolve() ?: return null
        if (psiElement is KtNamedFunction &&
            KotlinPsiHeuristics.isPossibleOperator(psiElement) &&
            psiElement.name == OperatorNameConventions.INVOKE.asString()
        ) {
            return psiElement
        }
        return null
    }

    override fun referencedClassOrCallable(calleeExpr: KtReferenceExpression): PsiElement? {
        return calleeExpr.mainReference.resolve()
    }

    override fun findDescriptor(
        element: KtElement,
        project: Project,
        editor: Editor?
    ): KtNamedDeclaration? {
        return when (element) {
            is KtParameter -> if (element.hasValOrVar()) element else null
            is KtCallableDeclaration -> element
            is KtClass -> element.primaryConstructor ?: if (element.allConstructors.isEmpty()) element else null
            else -> null
        }
    }

    override fun isVarargFunction(function: KtNamedDeclaration): Boolean {
        return function is KtNamedFunction && function.valueParameters.any { it.isVarArg }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isSynthetic(function: KtNamedDeclaration, context: KtElement): Boolean {
        return allowAnalysisOnEdt { analyze(context) { function.getSymbol().origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED } }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isLibrary(function: KtNamedDeclaration, context: KtElement): Boolean {
        val original = function.originalElement as? KtCallableDeclaration ?: return false
        return original.containingKtFile.isCompiled
    }

    override fun isJavaCallable(function: KtNamedDeclaration): Boolean {
        return false
    }

    override fun isDynamic(function: KtNamedDeclaration): Boolean {
        return false //todo
    }

    override fun getDeclaration(t: KtNamedDeclaration, project: Project): PsiElement {
        return t
    }

    override fun getDeclarationName(t: KtNamedDeclaration): String {
        return t.name!!
    }

    override fun runChangeSignature(
        project: Project, editor: Editor?, callableDescriptor: KtNamedDeclaration, context: PsiElement
    ) {
        when {
            callableDescriptor is KtFunction || callableDescriptor is KtClass -> {
                KotlinChangeSignatureDialog(project, editor, KotlinMethodDescriptor(callableDescriptor), context, null).show()
            }

            callableDescriptor is KtProperty || callableDescriptor is KtParameter && callableDescriptor.hasValOrVar() -> {
                KotlinChangePropertySignatureDialog(project, KotlinMethodDescriptor(callableDescriptor as KtCallableDeclaration)).show()
            }

            callableDescriptor is KtParameter -> {
                val ownerFunction = callableDescriptor.ownerFunction
                if (ownerFunction is KtCallableDeclaration) {
                    runChangeSignature(project, editor, ownerFunction, context)
                }
            }
        }

    }
}