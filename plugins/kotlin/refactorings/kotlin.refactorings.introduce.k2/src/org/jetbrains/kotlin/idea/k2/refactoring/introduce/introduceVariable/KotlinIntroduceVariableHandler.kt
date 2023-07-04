// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.SlowOperations
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeInModalWindow
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.getValueArgumentName
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.kotlin.utils.sure
import kotlin.math.min

object KotlinIntroduceVariableHandler : RefactoringActionHandler {

    val INTRODUCE_VARIABLE get() = KotlinBundle.message("introduce.variable")

    private val EXPRESSION_KEY = Key.create<Boolean>("EXPRESSION_KEY")
    private val COMMON_PARENT_KEY = Key.create<Boolean>("COMMON_PARENT_KEY")

    private var KtExpression.isOccurrence: Boolean by NotNullablePsiCopyableUserDataProperty(Key.create("OCCURRENCE"), false)

    private class IntroduceVariableContext(
        private val expression: KtExpression,
        private val nameSuggestions: List<Collection<String>>,
        private val commonContainer: PsiElement,
        private val commonParent: PsiElement,
        private val replaceOccurrence: Boolean,
        private val componentNames: List<String>,
    ) {
        private val psiFactory = KtPsiFactory(expression.project)

        var propertyRef: KtDeclaration? = null
        var reference: SmartPsiElementPointer<KtExpression>? = null
        val references = ArrayList<SmartPsiElementPointer<KtExpression>>()

        private fun findElementByOffsetAndText(offset: Int, text: String, newContainer: PsiElement): PsiElement? =
            newContainer.findElementAt(offset)?.parentsWithSelf?.firstOrNull { (it as? KtExpression)?.text == text }

        private fun replaceExpression(expressionToReplace: KtExpression, addToReferences: Boolean): KtExpression {
            val isActualExpression = expression == expressionToReplace

            val replacement = psiFactory.createExpression(nameSuggestions.single().first())
            var result = when {
                expressionToReplace.isLambdaOutsideParentheses() -> {
                    val functionLiteralArgument = expressionToReplace.getStrictParentOfType<KtLambdaArgument>()!!
                    val argumentName =
                        analyzeInModalWindow(expressionToReplace, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                            functionLiteralArgument.getValueArgumentName()
                        }
                    val newCallExpression = functionLiteralArgument.moveInsideParenthesesAndReplaceWith(replacement, argumentName)
                    newCallExpression.valueArguments.last().getArgumentExpression()!!
                }

                else -> expressionToReplace.replace(replacement) as KtExpression
            }

            result = result.removeTemplateEntryBracesIfPossible()

            if (addToReferences) {
                references.addIfNotNull(SmartPointerManager.createPointer(result))
            }

            if (isActualExpression) {
                reference = SmartPointerManager.createPointer(result)
            }

            return result
        }

        private fun runRefactoring(
            isVar: Boolean,
            expression: KtExpression,
            commonContainer: PsiElement,
            commonParent: PsiElement,
        ) {
            val initializer = (expression as? KtParenthesizedExpression)?.expression ?: expression
            val initializerText = if (initializer.mustBeParenthesizedInInitializerPosition()) "(${initializer.text})" else initializer.text

            val varOvVal = if (isVar) "var" else "val"

            var property: KtDeclaration = if (componentNames.isNotEmpty()) {
                buildString {
                    componentNames.indices.joinTo(this, prefix = "$varOvVal (", postfix = ")") { nameSuggestions[it].first() }
                    append(" = ")
                    append(initializerText)
                }.let { psiFactory.createDestructuringDeclaration(it) }
            } else {
                buildString {
                    append("$varOvVal ")
                    append(nameSuggestions.single().first())
                    if (KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY) {
                        append(": ").append(
                          analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                              (expression.getKtType() ?: builtinTypes.ANY).render(position = Variance.INVARIANT)
                          })
                    }
                    append(" = ")
                    append(initializerText)
                }.let { psiFactory.createProperty(it) }
            }

            var anchor = calculateAnchor(commonParent, commonContainer, listOf(expression)) ?: return
            val needBraces = commonContainer !is KtBlockExpression && commonContainer !is KtClassBody && commonContainer !is KtFile
            ApplicationManager.getApplication().runWriteAction {
                if (!needBraces) {
                    property = commonContainer.addBefore(property, anchor) as KtDeclaration
                    commonContainer.addBefore(psiFactory.createNewLine(), anchor)
                } else {
                    var emptyBody: KtExpression = psiFactory.createEmptyBody()
                    val firstChild = emptyBody.firstChild
                    emptyBody.addAfter(psiFactory.createNewLine(), firstChild)

                    if (replaceOccurrence) {
                        val exprAfterReplace = replaceExpression(expression, false)
                        exprAfterReplace.isOccurrence = true
                        if (anchor == expression) {
                            anchor = exprAfterReplace
                        }

                        var oldElement: PsiElement = commonContainer
                        if (commonContainer is KtWhenEntry) {
                            val body = commonContainer.expression
                            if (body != null) {
                                oldElement = body
                            }
                        } else if (commonContainer is KtContainerNode) {
                            val children = commonContainer.children
                            for (child in children) {
                                if (child is KtExpression) {
                                    oldElement = child
                                }
                            }
                        } //ugly logic to make sure we are working with right actual expression
                        var actualExpression = reference?.element ?: return@runWriteAction
                        var diff = actualExpression.textRange.startOffset - oldElement.textRange.startOffset
                        var actualExpressionText = actualExpression.text
                        val newElement = emptyBody.addAfter(oldElement, firstChild)
                        var elem: PsiElement? = findElementByOffsetAndText(diff, actualExpressionText, newElement)
                        if (elem != null) {
                            reference = SmartPointerManager.createPointer(elem as KtExpression)
                        }
                        emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                        property = emptyBody.addAfter(property, firstChild) as KtDeclaration
                        emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                        actualExpression = reference?.element ?: return@runWriteAction
                        diff = actualExpression.textRange.startOffset - emptyBody.textRange.startOffset
                        actualExpressionText = actualExpression.text
                        emptyBody = anchor.replace(emptyBody) as KtBlockExpression
                        elem = findElementByOffsetAndText(diff, actualExpressionText, emptyBody)
                        if (elem != null) {
                            reference = SmartPointerManager.createPointer(elem as KtExpression)
                        }

                        emptyBody.accept(object : KtTreeVisitorVoid() {
                            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                                if (!expression.isOccurrence) return

                                expression.isOccurrence = false
                                references.add(SmartPointerManager.createPointer(expression))
                            }
                        })
                    } else {
                        val parent = anchor.parent
                        val copyTo = parent.lastChild
                        val copyFrom = anchor.nextSibling

                        property = emptyBody.addAfter(property, firstChild) as KtDeclaration
                        emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                        if (copyFrom != null && copyTo != null) {
                            emptyBody.addRangeAfter(copyFrom, copyTo, property)
                            parent.deleteChildRange(copyFrom, copyTo)
                        }
                        emptyBody = anchor.replace(emptyBody) as KtBlockExpression
                    }
                    for (child in emptyBody.children) {
                        if (child is KtProperty) {
                            property = child
                        }
                    }
                    if (commonContainer is KtContainerNode) {
                        if (commonContainer.parent is KtIfExpression) {
                            val next = commonContainer.nextSibling
                            if (next != null) {
                                val nextNext = next.nextSibling
                                if (nextNext != null && nextNext.node.elementType == KtTokens.ELSE_KEYWORD) {
                                    if (next is PsiWhiteSpace) {
                                        next.replace(psiFactory.createWhiteSpace())
                                    }
                                }
                            }
                        }
                    }
                }
                if (!needBraces) {
                    if (expression.shouldReplaceOccurrence(commonContainer)) {
                        replaceExpression(expression, true)
                    } else {
                        val sibling = PsiTreeUtil.skipSiblingsBackward(expression, PsiWhiteSpace::class.java)
                        if (sibling == property) {
                            expression.parent.deleteChildRange(property.nextSibling, expression)
                        } else {
                            expression.delete()
                        }
                    }
                }
                propertyRef = property
                shortenReferences(property)
            }
        }

        fun runRefactoring(isVar: Boolean) {
            if (commonContainer !is KtDeclarationWithBody) return runRefactoring(
                isVar,
                expression,
                commonContainer,
                commonParent,
            )

            commonContainer.bodyExpression.sure { "Original body is not found: $commonContainer" }

            expression.putCopyableUserData(EXPRESSION_KEY, true)
            commonParent.putCopyableUserData(COMMON_PARENT_KEY, true)

            analyzeInModalWindow(commonContainer, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                ConvertToBlockBodyUtils.createContext(commonContainer, ShortenReferencesFacility.getInstance(), reformat = false)?.let {
                    ConvertToBlockBodyUtils.convert(commonContainer, it)
                }
                val newCommonContainer = commonContainer.bodyBlockExpression.sure { "New body is not found: $commonContainer" }
                val newExpression = newCommonContainer.findExpressionByCopyableDataAndClearIt(EXPRESSION_KEY)
                val newCommonParent = newCommonContainer.findElementByCopyableDataAndClearIt(COMMON_PARENT_KEY)

                runRefactoring(
                    isVar,
                    newExpression ?: return@analyzeInModalWindow,
                    newCommonContainer,
                    newCommonParent ?: return@analyzeInModalWindow
                )
            }
        }
    }

    private fun calculateAnchor(commonParent: PsiElement, commonContainer: PsiElement, allReplaces: List<KtExpression>): PsiElement? {
        if (commonParent != commonContainer) return commonParent.parentsWithSelf.firstOrNull { it.parent == commonContainer }

        val startOffset = allReplaces.fold(commonContainer.endOffset) { offset, expr ->
            min(offset, expr.startOffset)
        }

        return commonContainer.allChildren.lastOrNull { it.textRange.contains(startOffset) }
    }

    private fun PsiElement.isAssignmentLHS(): Boolean = parents.any {
        KtPsiUtil.isAssignment(it) && (it as KtBinaryExpression).left == this
    }

    private fun KtExpression.shouldReplaceOccurrence(container: PsiElement?): Boolean {
        val effectiveParent = (parent as? KtScriptInitializer)?.parent ?: parent
        return isUsedAsExpression(this) || container != effectiveParent
    }

    private fun isUsedAsExpression(element: KtExpression): Boolean {
        val parent = element.parent
        return if (parent is KtBlockExpression) {
            parent.lastBlockStatementOrThis() == element && parentIsUsedAsExpression(parent.parent)
        } else {
            parentIsUsedAsExpression(parent)
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun isUsedAsExpression(parent: PsiElement?): Boolean = allowAnalysisOnEdt {
        analyze(parent as KtExpression) {
            parent.isUsedAsExpression()
        }
    }

    private fun parentIsUsedAsExpression(element: PsiElement): Boolean {
        return when (val parent = element.parent) {
            is KtLoopExpression, is KtFile -> false
            is KtIfExpression, is KtWhenExpression -> isUsedAsExpression(parent)
            else -> true
        }
    }

    private fun KtElement.getContainer(): KtElement? {
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

    private fun KtContainerNode.isBadContainerNode(place: PsiElement): Boolean = when (val parent = parent) {
        is KtIfExpression -> parent.condition == place
        is KtLoopExpression -> parent.body != place
        is KtArrayAccessExpression -> true
        else -> false
    }

    private fun KtExpression.getOccurrenceContainer(): KtElement? {
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

    private fun showErrorHint(project: Project, editor: Editor?, @NlsContexts.DialogMessage message: String) {
        CommonRefactoringUtil.showErrorHint(project, editor, message, INTRODUCE_VARIABLE, HelpID.INTRODUCE_VARIABLE)
    }

    private fun KtExpression.chooseApplicableComponentNamesForVariableDeclaration(
        haveOccurrencesToReplace: Boolean,
        editor: Editor?,
        callback: (List<String>) -> Unit
    ) {
        if (haveOccurrencesToReplace) return callback(emptyList())
        return chooseApplicableComponentNames(this, editor, callback = callback)
    }

    private fun executeMultiDeclarationTemplate(
        project: Project,
        editor: Editor,
        declaration: KtDestructuringDeclaration,
        suggestedNames: List<Collection<String>>
    ) {
        StartMarkAction.canStart(editor)?.let { return }

        val builder = TemplateBuilderImpl(declaration)
        for ((index, entry) in declaration.entries.withIndex()) {
            val templateExpression = object : Expression() {
                private val lookupItems = suggestedNames[index].map { LookupElementBuilder.create(it) }.toTypedArray()

                override fun calculateQuickResult(context: ExpressionContext?) = TextResult(suggestedNames[index].first())

                override fun calculateResult(context: ExpressionContext?) = calculateQuickResult(context)

                override fun calculateLookupItems(context: ExpressionContext?) = lookupItems
            }
            builder.replaceElement(entry, templateExpression)
        }

        val startMarkAction = StartMarkAction.start(editor, project, INTRODUCE_VARIABLE)
        editor.caretModel.moveToOffset(declaration.startOffset)

        project.executeWriteCommand(INTRODUCE_VARIABLE) {
            TemplateManager.getInstance(project).startTemplate(
                editor,
                builder.buildInlineTemplate(),
                object : TemplateEditingAdapter() {
                    private fun finishMarkAction() = FinishMarkAction.finish(project, editor, startMarkAction)

                    override fun templateFinished(template: Template, brokenOff: Boolean) = finishMarkAction()

                    override fun templateCancelled(template: Template?) = finishMarkAction()
                })
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun doRefactoring(
        project: Project,
        editor: Editor?,
        expression: KtExpression,
        container: KtElement,
        occurrenceContainer: KtElement,
        isVar: Boolean,
    ) {
        val parent = expression.parent

        when {
            parent is KtQualifiedExpression -> {
                if (parent.receiverExpression != expression) {
                    return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.expression"))
                }
            }

            expression is KtStatementExpression ->
                return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.expression"))

            parent is KtOperationExpression && parent.operationReference == expression ->
                return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.expression"))
        }

        PsiTreeUtil.getNonStrictParentOfType(
            expression,
            KtTypeReference::class.java,
            KtConstructorCalleeExpression::class.java,
            KtSuperExpression::class.java,
            KtConstructorDelegationReferenceExpression::class.java,
            KtAnnotationEntry::class.java
        )?.let {
            return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.no.container"))
        }

        val typeString = allowAnalysisOnEdt {
            analyze(expression) {
                val expressionType = expression.getKtType()

                if (expressionType != null && expressionType isEqualTo builtinTypes.UNIT) {
                    showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.expression.has.unit.type"))
                }
                val renderer = KtTypeRendererForSource.WITH_SHORT_NAMES.with {
                    annotationsRenderer = annotationsRenderer.with { annotationFilter = KtRendererAnnotationsFilter.NONE }
                }
                expressionType?.render(renderer, position = Variance.INVARIANT)
            }
        }

        val isInplaceAvailable = editor != null && !isUnitTestMode()

        val callback = {
            val replaceOccurrence = expression.shouldReplaceOccurrence(container)

            val commonParent = expression.parent as KtElement
            var commonContainer = commonParent as? KtFile ?: commonParent.getContainer()!!
            if (commonContainer != container && container.isAncestor(commonContainer, true)) {
                commonContainer = container
            }

            expression.chooseApplicableComponentNamesForVariableDeclaration(replaceOccurrence, editor) { componentFunctions ->
                val suggestedNames = if (componentFunctions.isNotEmpty()) {
                    componentFunctions.map { listOf(it) }
                } else {
                    listOf(analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                        with(KotlinNameSuggester()) {
                            suggestExpressionNames(expression).toList()
                        }
                    })
                }

                val introduceVariableContext = IntroduceVariableContext(
                    expression,
                    suggestedNames,
                    commonContainer,
                    commonParent,
                    replaceOccurrence,
                    componentFunctions
                )

                project.executeCommand(INTRODUCE_VARIABLE, null) {
                    introduceVariableContext.runRefactoring(isVar)

                    val property = introduceVariableContext.propertyRef ?: return@executeCommand

                    if (editor == null) {
                        return@executeCommand
                    }

                    editor.caretModel.moveToOffset(property.textOffset)
                    editor.selectionModel.removeSelection()

                    if (!isInplaceAvailable) {
                        return@executeCommand
                    }

                    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

                    when (property) {
                        is KtProperty -> {
                            KotlinVariableInplaceIntroducer(
                                property,
                                introduceVariableContext.reference?.element,
                                introduceVariableContext.references.mapNotNull { it.element }.toTypedArray(),
                                suggestedNames.single(),
                                typeString,
                                project,
                                editor
                            ).startInplaceIntroduceTemplate()
                        }

                        is KtDestructuringDeclaration -> {
                            executeMultiDeclarationTemplate(project, editor, property, suggestedNames)
                        }

                        else -> throw AssertionError("Unexpected declaration: ${property.getElementTextWithContext()}")
                    }
                }
            }

        }
        if (isInplaceAvailable) {
            ApplicationManager.getApplication().invokeLater {
                SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use { _ -> callback() }
            }
        } else {
            callback()
        }
    }

    private fun PsiElement.isFunExpressionOrLambdaBody(): Boolean {
        if (isFunctionalExpression()) return true
        val parent = parent as? KtFunction ?: return false
        return parent.bodyExpression == this && (parent is KtFunctionLiteral || parent.isFunctionalExpression())
    }

    private fun KtExpression.getCandidateContainers(): List<Pair<KtElement, KtElement>> {
        val firstContainer = getContainer() ?: return emptyList()
        val firstOccurrenceContainer = getOccurrenceContainer() ?: return emptyList()

        val containers = SmartList(firstContainer)
        val occurrenceContainers = SmartList(firstOccurrenceContainer)

        if (!firstContainer.isFunExpressionOrLambdaBody()) return listOf(firstContainer to firstOccurrenceContainer)

        val lambdasAndContainers = ArrayList<Pair<KtExpression, KtElement>>().apply {
            var container = firstContainer
            do {
                var lambda: KtExpression = container.getNonStrictParentOfType<KtFunction>()!!
                if (lambda is KtFunctionLiteral) lambda =
                    lambda.parent as? KtLambdaExpression ?: return@apply //if (!isResolvableNextTo(lambda)) return@apply
                container = lambda.getContainer() ?: return@apply
                add(lambda to container)
            } while (container.isFunExpressionOrLambdaBody())
        }

        lambdasAndContainers.mapTo(containers) { it.second }
        lambdasAndContainers.mapTo(occurrenceContainers) { it.first.getOccurrenceContainer() }
        return ArrayList<Pair<KtElement, KtElement>>().apply {
            for ((container, occurrenceContainer) in (containers zip occurrenceContainers)) {
                if (occurrenceContainer == null) continue
                add(container to occurrenceContainer)
            }
        }
    }

    private fun doRefactoring(
        project: Project,
        editor: Editor?,
        expressionToExtract: KtExpression?,
        isVar: Boolean,
        selectContainer: (List<Pair<KtElement, KtElement>>, (Pair<KtElement, KtElement>) -> Unit) -> Unit
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

        selectContainer(candidateContainers) { (container, occurrenceContainer) ->
            doRefactoring(
                project, editor, expression, container, occurrenceContainer, isVar
            )
        }
    }

    fun doRefactoring(
        project: Project,
        editor: Editor?,
        expressionToExtract: KtExpression?,
        isVar: Boolean,
    ) = doRefactoring(
        project,
        editor,
        expressionToExtract,
        isVar,
    ) { candidateContainers, doRefactoring ->
        if (editor == null) {
            doRefactoring(candidateContainers.first())
        } else if (isUnitTestMode()) {
            doRefactoring(candidateContainers.last())
        } else {
            chooseContainerElementIfNecessary(
                candidateContainers, editor,
                KotlinBundle.message("text.select.target.code.block"), true, { it.first },
                doRefactoring
            )
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile?, dataContext: DataContext?) {
        if (file !is KtFile) return

        try {
            selectElement(editor, file, ElementKind.EXPRESSION) {
                doRefactoring(
                    project,
                    editor,
                    it as KtExpression?,
                    KotlinCommonRefactoringSettings.getInstance().INTRODUCE_DECLARE_WITH_VAR
                )
            }
        } catch (e: IntroduceRefactoringException) {
            showErrorHint(project, editor, e.message!!)
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //do nothing
    }
}
