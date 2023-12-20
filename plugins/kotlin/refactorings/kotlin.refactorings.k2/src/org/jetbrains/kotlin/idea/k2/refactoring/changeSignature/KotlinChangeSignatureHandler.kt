// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.actions.ChangeSignatureAction
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui.KotlinChangePropertySignatureDialog
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui.KotlinChangeSignatureDialog
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureHandlerBase
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions

object KotlinChangeSignatureHandler : KotlinChangeSignatureHandlerBase() {
    override fun asInvokeOperator(call: KtCallElement?): PsiElement? {
        val psiElement = call?.mainReference?.resolve() ?: return null
        if (psiElement is KtNamedFunction && KotlinPsiHeuristics.isPossibleOperator(psiElement) && psiElement.name == OperatorNameConventions.INVOKE.asString()) {
            return psiElement
        }
        return null
    }

    override fun invokeChangeSignature(
        element: KtElement, context: PsiElement, project: Project, editor: Editor?, dataContext: DataContext?
    ) {
        val callableDeclaration = findDeclaration(element, context, project, editor) ?: return
        if (callableDeclaration !is KtNamedDeclaration) {
            ChangeSignatureAction.getChangeSignatureHandler(callableDeclaration)?.invoke(project, arrayOf(callableDeclaration), dataContext)
            return
        }
        runChangeSignature(project, editor, callableDeclaration, context)
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun findDeclaration(element: KtElement, context: PsiElement, project: Project, editor: Editor?): PsiElement? {
        val ktModule = ProjectStructureProvider.getInstance(project).getModule(context, null)
        return allowAnalysisOnEdt {
            analyze(ktModule) {
                val ktSymbol = when (element) {
                    is KtParameter -> if (element.hasValOrVar()) element.getSymbol() else null
                    is KtCallableDeclaration -> element.getSymbol()
                    is KtClass -> element.primaryConstructor?.getSymbol()
                        ?: if (element.allConstructors.isEmpty()) element.getSymbol() else null
                    is KtReferenceExpression -> element.mainReference.resolveToSymbols().firstOrNull()
                        ?.takeIf { it !is KtValueParameterSymbol || it.generatedPrimaryConstructorProperty != null }
                    else -> null
                }

                val elementKind = when {
                    ktSymbol == null -> InapplicabilityKind.Null
                    ktSymbol is KtFunctionSymbol && ktSymbol.valueParameters.any { it.isVararg } -> InapplicabilityKind.Varargs
                    ktSymbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED -> InapplicabilityKind.Synthetic
                    ktSymbol.origin == KtSymbolOrigin.LIBRARY -> InapplicabilityKind.Library
                    else -> null
                }

                if (elementKind != null) {
                    val message = RefactoringBundle.getCannotRefactorMessage(
                        elementKind.description
                    )

                    CommonRefactoringUtil.showErrorHint(
                        project, editor, message, RefactoringBundle.message("changeSignature.refactoring.name"), HelpID.CHANGE_SIGNATURE
                    )
                    null
                } else ktSymbol?.psi
            }
        }
    }


    private fun runChangeSignature(
        project: Project, editor: Editor?, callableDescriptor: KtNamedDeclaration, context: PsiElement
    ) {

        val superMethods = checkSuperMethods(callableDescriptor, emptyList(), RefactoringBundle.message("to.refactor"))

        val callableToRefactor = superMethods.firstOrNull() as? KtNamedDeclaration ?: return
        when {
            callableToRefactor is KtFunction || callableToRefactor is KtClass -> {
                KotlinChangeSignatureDialog(project, editor, KotlinMethodDescriptor(callableToRefactor), context, null).show()
            }

            callableToRefactor is KtProperty || callableToRefactor is KtParameter && callableToRefactor.hasValOrVar() -> {
                KotlinChangePropertySignatureDialog(project, KotlinMethodDescriptor(callableToRefactor as KtCallableDeclaration)).show()
            }

            callableToRefactor is KtParameter -> {
                val ownerFunction = callableToRefactor.ownerFunction
                if (ownerFunction is KtCallableDeclaration) {
                    runChangeSignature(project, editor, ownerFunction, context)
                }
            }
        }
    }
}