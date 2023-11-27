// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper.Containers
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.isFunctionalExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

abstract class KotlinIntroduceVariableHandler : RefactoringActionHandler {
    abstract fun doRefactoringWithSelectedTargetContainer(
        project: Project,
        editor: Editor?,
        expression: KtExpression,
        containers: Containers,
        isVar: Boolean,
        occurrencesToReplace: List<KtExpression>? = null,
        onNonInteractiveFinish: ((KtDeclaration) -> Unit)? = null,
    )

    abstract fun KtExpression.getCandidateContainers(): List<Containers>

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is KtFile) return

        try {
            selectElement(editor, file, ElementKind.EXPRESSION) {
                collectCandidateTargetContainersAndDoRefactoring(project, editor, it as KtExpression?, isVar = false)
            }
        } catch (e: IntroduceRefactoringException) {
            showErrorHint(project, editor, e.message!!)
        }
    }

    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext) {
        // do nothing
    }

    fun collectCandidateTargetContainersAndDoRefactoring(
        project: Project,
        editor: Editor?,
        expressionToExtract: KtExpression?,
        isVar: Boolean,
        occurrencesToReplace: List<KtExpression>? = null,
        targetContainer: KtElement? = null,
        onNonInteractiveFinish: ((KtDeclaration) -> Unit)? = null
    ) {
        val expression = expressionToExtract?.let { KtPsiUtil.safeDeparenthesize(it) }
            ?: return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.expression"))

        if (expression.isAssignmentLHS()) {
            return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.expression"))
        }

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, expression)) return

        val candidateContainers = expression.getCandidateContainers().ifEmpty {
            return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.container"))
        }

        selectTargetContainerAndDoRefactoring(editor, targetContainer, candidateContainers) { containers ->
            doRefactoringWithSelectedTargetContainer(
                project, editor, expression, containers,
                isVar, occurrencesToReplace, onNonInteractiveFinish
            )
        }
    }

    protected fun selectTargetContainerAndDoRefactoring(
        editor: Editor?,
        targetContainer: KtElement?,
        candidateContainers: List<Containers>,
        doRefactoring: (Containers) -> Unit,
    ) {
        if (targetContainer != null) {
            val foundPair = candidateContainers.find { it.targetContainer.textRange == targetContainer.textRange }
            if (foundPair != null) {
                doRefactoring(foundPair)
            }
        } else if (editor == null) {
            doRefactoring(candidateContainers.first())
        } else if (isUnitTestMode()) {
            doRefactoring(candidateContainers.last())
        } else {
            chooseContainerElementIfNecessary(
                candidateContainers, editor,
                KotlinBundle.message("text.select.target.code.block"), true, { it.targetContainer },
                doRefactoring
            )
        }
    }

    protected companion object {
        val INTRODUCE_VARIABLE get() = KotlinBundle.message("introduce.variable")

        fun findElementByOffsetAndText(offset: Int, text: String, newContainer: PsiElement): PsiElement? =
            newContainer.findElementAt(offset)?.parentsWithSelf?.firstOrNull { (it as? KtExpression)?.text == text }

        fun PsiElement.isAssignmentLHS(): Boolean = parents.any {
            KtPsiUtil.isAssignment(it) && (it as KtBinaryExpression).left == this
        }

        fun showErrorHint(project: Project, editor: Editor?, @NlsContexts.DialogMessage message: String) {
            CommonRefactoringUtil.showErrorHint(project, editor, message, INTRODUCE_VARIABLE, HelpID.INTRODUCE_VARIABLE)
        }

        fun KtElement.getContainer(): KtElement? {
            if (this is KtBlockExpression) return this

            return (parentsWithSelf.zip(parents)).firstOrNull {
                val (place, parent) = it
                when (parent) {
                    is KtContainerNode -> !parent.isBadContainerNode(place)
                    is KtBlockExpression -> true
                    is KtWhenEntry -> place == parent.expression
                    is KtDeclarationWithBody -> parent.bodyExpression == place
                    is KtClassBody -> true
                    is KtFile -> true
                    else -> false
                }
            }?.second as? KtElement
        }

        fun KtExpression.getOccurrenceContainer(): KtElement? {
            var result: KtElement? = null
            for ((place, parent) in parentsWithSelf.zip(parents)) {
                when {
                    parent is KtContainerNode && place !is KtBlockExpression && !parent.isBadContainerNode(place) -> result = parent
                    parent is KtClassBody || parent is KtFile -> return result ?: parent as? KtElement
                    parent is KtBlockExpression -> result = parent
                    parent is KtWhenEntry && place !is KtBlockExpression -> result = parent
                    parent is KtDeclarationWithBody && parent.bodyExpression == place && place !is KtBlockExpression -> result = parent
                }
            }

            return null
        }

        fun KtContainerNode.isBadContainerNode(place: PsiElement): Boolean = when (val parent = parent) {
            is KtIfExpression -> parent.condition == place
            is KtLoopExpression -> parent.body != place
            is KtArrayAccessExpression -> true
            else -> false
        }

        fun PsiElement.isFunExpressionOrLambdaBody(): Boolean {
            if (isFunctionalExpression()) return true
            val parent = parent as? KtFunction ?: return false
            return parent.bodyExpression == this && (parent is KtFunctionLiteral || parent.isFunctionalExpression())
        }

        fun isInplaceAvailable(editor: Editor?, project: Project) = when {
            editor == null -> false
            isUnitTestMode() -> (TemplateManager.getInstance(project) as? TemplateManagerImpl)?.shouldSkipInTests() == false
            else -> true
        }
    }
}