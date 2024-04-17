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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper.Containers
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationReferenceExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStatementExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
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

    abstract fun KtExpression.findOccurrences(occurrenceContainer: KtElement): List<KtExpression>

    /**
     * @param contained expression, which is:
     * * parent of extracted expression before refactoring
     * * sibling of introduced variable after refactoring in case [container] was chosen
     */
    protected data class ContainerWithContained(val container: KtElement, val contained: KtExpression)

    /**
     * @param containersWithContainedLambdas containers of nested lambdas with corresponding lambdas. For the following example:
     * ```
     * fun test() {
     *     bar { p -> foo { <selection>p + 3</selection> } }
     * }
     * ```
     * [containersWithContainedLambdas] consists of:
     * * block expression of `bar { ... }` - container of `foo { ... }`
     * * block expression of `fun test() { ... }` - container of `bar { ... }`
     *
     * The second container is inapplicable so the resulting sequence will contain only the first one.
     */
    protected abstract fun filterContainersWithContainedLambdasByAnalyze(
        containersWithContainedLambdas: Sequence<ContainerWithContained>,
        physicalExpression: KtExpression,
        referencesFromExpressionToExtract: List<KtReferenceExpression>,
    ): Sequence<ContainerWithContained>

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is KtFile) return

        try {
            selectElement(editor, file, false, listOf(ElementKind.EXPRESSION)) {
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

    fun KtExpression.getCandidateContainers(): List<Containers> {
        val physicalExpression = substringContextOrThis

        val firstContainer = physicalExpression.getContainer() ?: return emptyList()
        val firstOccurrenceContainer = physicalExpression.getOccurrenceContainer() ?: return emptyList()

        val lambda = firstContainer.getLambdaForFunExpressionOrLambdaBody()
        val lambdaContainer = lambda?.getContainer()

        if (lambda == null || lambdaContainer == null) return listOf(Containers(firstContainer, firstOccurrenceContainer))

        // `lambda` != null, which means that expression is inside lambda; if expression doesn't use any of lambda's arguments, then it's
        // likely that it should be introduced outside the lambda, that's why here we suggest other containers beside lambda, see KTIJ-3457

        val containersWithContainedLambdas = generateSequence(ContainerWithContained(lambdaContainer, lambda)) { (container, _) ->
            val nextLambda = container.getLambdaForFunExpressionOrLambdaBody() ?: return@generateSequence null
            val nextLambdaContainer = nextLambda.getContainer() ?: return@generateSequence null

            ContainerWithContained(nextLambdaContainer, nextLambda)
        }.let {
            val contentRange = extractableSubstringInfo?.contentRange
            val references = physicalExpression.collectDescendantsOfType<KtReferenceExpression> {
                contentRange == null || contentRange.contains(it.textRange)
            }
            filterContainersWithContainedLambdasByAnalyze(it, physicalExpression, references)
        }.toList()

        val containers = SmartList(firstContainer)
        val occurrenceContainers = SmartList(firstOccurrenceContainer)

        containersWithContainedLambdas.mapTo(containers) { it.container }
        containersWithContainedLambdas.mapTo(occurrenceContainers) { it.contained.getOccurrenceContainer() }
        return ArrayList<Containers>().apply {
            for ((container, occurrenceContainer) in (containers zip occurrenceContainers)) {
                if (occurrenceContainer == null) continue
                add(Containers(container, occurrenceContainer))
            }
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

    protected fun isRefactoringApplicableByPsi(project: Project, editor: Editor?, expression: KtExpression): Boolean {
        val physicalExpression = expression.substringContextOrThis
        val parent = physicalExpression.parent

        val isApplicable = when {
            parent is KtQualifiedExpression -> parent.receiverExpression == physicalExpression
            parent is KtOperationExpression && parent.operationReference == physicalExpression -> false
            else -> physicalExpression !is KtStatementExpression
        }
        if (!isApplicable) {
            showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.expression"))
            return false
        }

        PsiTreeUtil.getNonStrictParentOfType(
            physicalExpression,
            KtTypeReference::class.java,
            KtConstructorCalleeExpression::class.java,
            KtSuperExpression::class.java,
            KtConstructorDelegationReferenceExpression::class.java,
            KtAnnotationEntry::class.java
        )?.let {
            showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.container"))
            return false
        }

        return true
    }

    protected companion object {
        val INTRODUCE_VARIABLE get() = KotlinBundle.message("introduce.variable")

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

        fun PsiElement.getLambdaForFunExpressionOrLambdaBody(): KtExpression? {
            if (isFunctionalExpression()) return this as? KtFunction
            val parent = (parent as? KtFunction)?.takeIf { it.bodyExpression == this }

            return when {
                parent is KtFunctionLiteral -> parent.parent as? KtLambdaExpression
                parent?.isFunctionalExpression() == true -> parent
                else -> null
            }
        }

        fun isInplaceAvailable(editor: Editor?, project: Project) = when {
            editor == null -> false
            isUnitTestMode() -> (TemplateManager.getInstance(project) as? TemplateManagerImpl)?.shouldSkipInTests() == false
            else -> true
        }
    }
}