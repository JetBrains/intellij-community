// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.psi.*
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class ReplacementPerformer<TElement : KtElement>(
    protected val codeToInline: MutableCodeToInline,
    protected var elementToBeReplaced: TElement
) {
    protected val psiFactory = KtPsiFactory(elementToBeReplaced.project)

    abstract fun doIt(postProcessing: (PsiChildRange) -> PsiChildRange): TElement?
}

abstract class AbstractSimpleReplacementPerformer<TElement : KtElement>(
    codeToInline: MutableCodeToInline,
    elementToBeReplaced: TElement
) : ReplacementPerformer<TElement>(codeToInline, elementToBeReplaced) {
    protected abstract fun createDummyElement(mainExpression: KtExpression): TElement

    protected open fun rangeToElement(range: PsiChildRange): TElement {
        assert(range.first == range.last)

        @Suppress("UNCHECKED_CAST")
        return range.first as TElement
    }

    final override fun doIt(postProcessing: (PsiChildRange) -> PsiChildRange): TElement {
        assert(codeToInline.statementsBefore.isEmpty())
        val mainExpression = codeToInline.mainExpression ?: error("mainExpression mustn't be null")

        val dummyElement = createDummyElement(mainExpression)
        val replaced = elementToBeReplaced.replace(dummyElement)

        codeToInline.performPostInsertionActions(listOf(replaced))

        return rangeToElement(postProcessing(PsiChildRange.singleElement(replaced)))
    }
}

class AnnotationEntryReplacementPerformer(
    codeToInline: MutableCodeToInline,
    elementToBeReplaced: KtAnnotationEntry
) : AbstractSimpleReplacementPerformer<KtAnnotationEntry>(codeToInline, elementToBeReplaced) {
    private val useSiteTarget = elementToBeReplaced.useSiteTarget?.getAnnotationUseSiteTarget()

    override fun createDummyElement(mainExpression: KtExpression): KtAnnotationEntry =
        createByPattern("@Dummy($0)", mainExpression) { psiFactory.createAnnotationEntry(it) }

    override fun rangeToElement(range: PsiChildRange): KtAnnotationEntry {
        val useSiteTargetText = useSiteTarget?.renderName?.let { "$it:" } ?: ""
        val isFileUseSiteTarget = useSiteTarget == AnnotationUseSiteTarget.FILE

        assert(range.first == range.last)
        assert(range.first is KtAnnotationEntry)
        val annotationEntry = range.first as KtAnnotationEntry
        val text = annotationEntry.valueArguments.single().getArgumentExpression()!!.text
        val newAnnotationEntry = if (isFileUseSiteTarget)
            psiFactory.createFileAnnotation(text)
        else
            psiFactory.createAnnotationEntry("@$useSiteTargetText$text")
        return annotationEntry.replaced(newAnnotationEntry)
    }
}

class SuperTypeCallEntryReplacementPerformer(
    codeToInline: MutableCodeToInline,
    elementToBeReplaced: KtSuperTypeCallEntry
) : AbstractSimpleReplacementPerformer<KtSuperTypeCallEntry>(codeToInline, elementToBeReplaced) {
    override fun createDummyElement(mainExpression: KtExpression): KtSuperTypeCallEntry {
        val text = if (mainExpression is KtCallElement && mainExpression.lambdaArguments.isNotEmpty()) {
            callWithoutLambdaArguments(mainExpression)
        } else {
            mainExpression.text
        }

        return psiFactory.createSuperTypeCallEntry(text)
    }
}

private fun callWithoutLambdaArguments(callExpression: KtCallElement): String {
    val copy = callExpression.copy() as KtCallElement
    val lambdaArgument = copy.lambdaArguments.first()

    val argumentExpression = lambdaArgument.getArgumentExpression() ?: return callExpression.text
    return lambdaArgument.moveInsideParenthesesAndReplaceWith(
        replacement = argumentExpression,
        lambdaArgumentName = null,
    ).text ?: callExpression.text
}

class ExpressionReplacementPerformer(
    codeToInline: MutableCodeToInline,
    expressionToBeReplaced: KtExpression
) : ReplacementPerformer<KtExpression>(codeToInline, expressionToBeReplaced) {

    private fun KtExpression.replacedWithStringTemplate(templateExpression: KtStringTemplateExpression): KtExpression? {
        val parent = this.parent

        return if (parent is KtStringTemplateEntryWithExpression
            // Do not mix raw and non-raw templates
            && parent.parent.firstChild.text == templateExpression.firstChild.text
        ) {

            val entriesToAdd = templateExpression.entries
            val grandParentTemplateExpression = parent.parent as KtStringTemplateExpression
            val result = if (entriesToAdd.isNotEmpty()) {
                val lastEntry = parent.prevSibling
                grandParentTemplateExpression.addRangeBefore(entriesToAdd.first(), entriesToAdd.last(), parent)
                val lastNewEntry = parent.prevSibling
                lastEntry.safeAs<KtSimpleNameStringTemplateEntry>()?.addBracesIfNeeded(lastNewEntry)
                lastNewEntry.safeAs<KtSimpleNameStringTemplateEntry>()?.addBracesIfNeeded(parent.nextSibling)
                grandParentTemplateExpression
            } else null

            parent.delete()
            result
        } else {
            replaced(templateExpression)
        }
    }

    private fun KtSimpleNameStringTemplateEntry.addBracesIfNeeded(nextElement: PsiElement) {
        if (canPlaceAfterSimpleNameEntry(nextElement)) return
        val expression = this.expression ?: return
        replace(KtPsiFactory(project).createBlockStringTemplateEntry(expression))
    }

    override fun doIt(postProcessing: (PsiChildRange) -> PsiChildRange): KtExpression? {
        val insertedStatements = ArrayList<KtExpression>()
        for (statement in codeToInline.statementsBefore) {
            val statementToUse = statement.copy()
            val anchor = findOrCreateBlockToInsertStatement()
            val block = anchor.parent as KtBlockExpression

            val inserted = block.addBefore(statementToUse, anchor) as KtExpression
            block.addBefore(psiFactory.createNewLine(), anchor)
            block.addBefore(psiFactory.createNewLine(), inserted)
            insertedStatements.add(inserted)
        }

        val replaced: KtExpression? = when (val mainExpression = codeToInline.mainExpression?.copied()) {
            is KtStringTemplateExpression -> elementToBeReplaced.replacedWithStringTemplate(mainExpression)

            is KtExpression -> elementToBeReplaced.replaced(mainExpression)

            else -> {
                // NB: Unit is never used as expression
                val stub = elementToBeReplaced.replaced(psiFactory.createExpression("0"))

                @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
                val canDropElementToBeReplaced = allowAnalysisFromWriteAction {
                    allowAnalysisOnEdt {
                        analyze(stub) {
                            !stub.isUsedAsExpression
                        }
                    }
                }
                if (canDropElementToBeReplaced) {
                    val parent = stub.parents.first { it !is KtParenthesizedExpression }
                    if (parent is KtWhenExpression && parent.subjectExpression?.safeDeparenthesize() == stub && parent.leftParenthesis != null && parent.rightParenthesis != null) {
                        parent.deleteChildRange(parent.leftParenthesis, parent.rightParenthesis)
                    } else if (parent is KtQualifiedExpression && parent.selectorExpression?.safeDeparenthesize() == stub) {
                        parent.replaced(parent.receiverExpression)
                    } else {
                        stub.delete()
                    }
                    null
                } else {
                    stub.replaced(psiFactory.createExpression("Unit"))
                }
            }
        }

        codeToInline.performPostInsertionActions(insertedStatements + listOfNotNull(replaced))

        var range = if (replaced != null) {
            if (insertedStatements.isEmpty()) {
                PsiChildRange.singleElement(replaced)
            } else {
                val statement = insertedStatements.first()
                PsiChildRange(statement, replaced.parentsWithSelf.first { it.parent == statement.parent })
            }
        } else {
            if (insertedStatements.isEmpty()) {
                PsiChildRange.EMPTY
            } else {
                PsiChildRange(insertedStatements.first(), insertedStatements.last())
            }
        }

        val listener = replaced?.let { TrackExpressionListener(it) }
        listener?.attach()
        try {
            range = postProcessing(range)
        } finally {
            listener?.detach()
        }

        val resultExpression = listener?.result

        // simplify "${x}" to "$x"
        val templateEntry = resultExpression?.parent as? KtBlockStringTemplateEntry
        if (templateEntry != null && templateEntry.canDropCurlyBrackets()) {
            return templateEntry.dropCurlyBrackets().expression
        }

        return resultExpression ?: range.last as? KtExpression
    }

    /**
     * Returns statement in a block to insert statement before it
     */
    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    private fun findOrCreateBlockToInsertStatement(): KtExpression {
        for (element in elementToBeReplaced.parentsWithSelf) {
            val parent = element.parent
            when (element) {
                is KtContainerNodeForControlStructureBody -> { // control statement without block
                    return element.expression!!.replaceWithBlock()
                }

                is KtExpression -> {
                    if (parent is KtWhenEntry) { // when entry without block
                        return element.replaceWithBlock()
                    }

                    if (parent is KtDeclarationWithBody) {
                        withElementToBeReplacedPreserved {
                            val context = allowAnalysisFromWriteAction {
                                allowAnalysisOnEdt {
                                    analyze(parent) {
                                        ConvertToBlockBodyUtils.createContext(
                                          parent,
                                          reformat = false
                                        )
                                    }
                                }
                            }
                            if (context != null) {
                                ConvertToBlockBodyUtils.convert(parent, context)
                            }
                            parent
                        }
                        return (parent.bodyExpression as KtBlockExpression).statements.single()
                    }

                    if (parent is KtBlockExpression) return element
                    if (parent is KtBinaryExpression) {
                        return element.replaceWithRun()
                    }
                }
            }
        }

        elementToBeReplaced = elementToBeReplaced.replaceWithRunBlockAndGetExpression()
        return elementToBeReplaced
    }

    private fun KtElement.replaceWithRun(): KtExpression = withElementToBeReplacedPreserved {
        replaceWithRunBlockAndGetExpression()
    }

    private fun KtElement.replaceWithRunBlockAndGetExpression(): KtExpression {
        val runExpressionText = if (getStrictParentOfType<KtDelegatedSuperTypeEntry>() != null) "run({ $0 })" else "run { $0 }"
        val runExpression = psiFactory.createExpressionByPattern(runExpressionText, this) as KtCallExpression
        val runAfterReplacement = this.replaced(runExpression)
        val ktLambdaArgument = runAfterReplacement.valueArguments[0]
        return (ktLambdaArgument.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression?.statements?.singleOrNull()
            ?: throw KotlinExceptionWithAttachments("cant get body expression for $ktLambdaArgument")
                .withPsiAttachment("ktLambdaArgument", ktLambdaArgument)
    }

    private fun KtExpression.replaceWithBlock(): KtExpression = withElementToBeReplacedPreserved {
        replaced(KtPsiFactory(project).createSingleStatementBlock(this))
    }.statements.single()

    private fun <TElement : KtElement> withElementToBeReplacedPreserved(action: () -> TElement): TElement {
        elementToBeReplaced.putCopyableUserData(ELEMENT_TO_BE_REPLACED_KEY, Unit)
        val result = action()
        elementToBeReplaced = result.findDescendantOfType { it.getCopyableUserData(ELEMENT_TO_BE_REPLACED_KEY) != null }
            ?: error("Element `elementToBeReplaced` not found")

        elementToBeReplaced.putCopyableUserData(ELEMENT_TO_BE_REPLACED_KEY, null)
        return result
    }

    private class TrackExpressionListener(expression: KtExpression) : PsiTreeChangeAdapter() {
        private var expression: KtExpression? = expression
        private val manager = expression.manager

        fun attach() {
            manager.addPsiTreeChangeListener(this)
        }

        fun detach() {
            manager.removePsiTreeChangeListener(this)
        }

        val result: KtExpression?
            get() = expression?.takeIf { it.isValid }

        override fun childReplaced(event: PsiTreeChangeEvent) {
            if (event.oldChild == expression) {
                expression = event.newChild as? KtExpression
            }
        }
    }
}

private val ELEMENT_TO_BE_REPLACED_KEY = Key<Unit>("ELEMENT_TO_BE_REPLACED_KEY")
