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
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.isLocal
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
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
        runChangeSignature(project, editor, callableDeclaration, dataContext)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun findDeclaration(element: PsiElement, context: PsiElement, project: Project, editor: Editor?): PsiElement? {
        if (element !is KtElement) return element
        val module = context.getKaModule(project, useSiteModule = null)
        return allowAnalysisOnEdt {
            analyze(module) {
                val ktSymbol = when (element) {
                    is KtParameter -> {
                        if (element.hasValOrVar()) element.symbol else null
                    }
                    is KtCallableDeclaration -> {
                        element.symbol
                    }
                    is KtClass -> {
                        element.primaryConstructor?.symbol
                            ?: if (element.allConstructors.isEmpty()) element.takeUnless { it.isInterface() }?.symbol else null
                    }
                    is KtReferenceExpression -> {
                        val symbol = element.mainReference.resolveToSymbol()
                        when {
                          symbol is KaValueParameterSymbol && symbol.generatedPrimaryConstructorProperty == null -> null
                          symbol is KaConstructorSymbol && symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED -> symbol.containingDeclaration
                          else -> symbol
                        }
                    }
                    else -> {
                        null
                    }
                }

                val elementKind = when {
                    ktSymbol == null -> InapplicabilityKind.Null
                    ktSymbol is KaLocalVariableSymbol -> InapplicabilityKind.Null
                    ktSymbol is KaNamedFunctionSymbol && ktSymbol.valueParameters.any { it.isVararg } -> InapplicabilityKind.Varargs
                    ktSymbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED -> InapplicabilityKind.Synthetic
                    ktSymbol.origin == KaSymbolOrigin.LIBRARY -> InapplicabilityKind.Library
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
        project: Project, editor: Editor?, callableDescriptor: KtNamedDeclaration, dataContext: DataContext?
    ) {

        val superMethods = checkSuperMethods(callableDescriptor, emptyList(), RefactoringBundle.message("to.refactor"))

        val callableToRefactor = findDeclaration(superMethods.firstOrNull() ?: return, callableDescriptor, project, editor) ?: return

        when {
            callableToRefactor !is KtNamedDeclaration -> {
                ChangeSignatureAction.getChangeSignatureHandler(callableToRefactor)?.invoke(project, arrayOf(callableToRefactor), dataContext)
            }

            callableToRefactor is KtFunction || callableToRefactor is KtClass -> {
                KotlinChangeSignatureDialog(project, editor, KotlinMethodDescriptor(callableToRefactor), callableDescriptor, null).show()
            }

            callableToRefactor is KtProperty || callableToRefactor is KtParameter && callableToRefactor.hasValOrVar() -> {
                KotlinChangePropertySignatureDialog(project, KotlinMethodDescriptor(callableToRefactor as KtCallableDeclaration)).show()
            }

            callableToRefactor is KtParameter -> {
                val ownerFunction = callableToRefactor.ownerFunction
                if (ownerFunction is KtCallableDeclaration) {
                    runChangeSignature(project, editor, ownerFunction, dataContext)
                }
            }
        }
    }
}