// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SlowOperations
import com.intellij.util.application
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Companion.suggestNameByName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper.Containers
import org.jetbrains.kotlin.idea.refactoring.introduce.findExpressionByCopyableDataAndClearIt
import org.jetbrains.kotlin.idea.refactoring.introduce.mustBeParenthesizedInInitializerPosition
import org.jetbrains.kotlin.idea.refactoring.introduce.removeTemplateEntryBracesIfPossible
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.sure
import kotlin.math.min

object K2IntroduceVariableHandler : KotlinIntroduceVariableHandler() {
    private val EXPRESSION_KEY = Key.create<Boolean>("EXPRESSION_KEY")

    private var KtExpression.isOccurrence: Boolean by NotNullablePsiCopyableUserDataProperty(Key.create("OCCURRENCE"), false)

    private class IntroduceVariableContext(
        private val expression: KtExpression,
        private val nameSuggestions: List<Collection<String>>,
        private val container: PsiElement,
        private val replaceOccurrence: Boolean,
        private val expressionRenderedType: String,
        private val componentNames: List<String>,
        renderedTypeArguments: String?,
    ) {
        private val psiFactory = KtPsiFactory(expression.project)

        var introducedVariablePointer: SmartPsiElementPointer<KtDeclaration>? = null
        var reference: SmartPsiElementPointer<KtExpression>? = null
        val references = ArrayList<SmartPsiElementPointer<KtExpression>>()
        var mustSpecifyTypeExplicitly = false
        var renderedTypeArgumentsIfMightBeNeeded: String? = renderedTypeArguments

        private fun replaceExpression(
            expressionToReplace: KtExpression,
            addToReferences: Boolean,
            lambdaArgumentName: Name?
        ): KtExpression {
            val isActualExpression = expression == expressionToReplace

            val replacement = psiFactory.createExpression(nameSuggestions.single().first())
            var result = when {
                expressionToReplace.isLambdaOutsideParentheses() -> {
                    val functionLiteralArgument = expressionToReplace.getStrictParentOfType<KtLambdaArgument>()!!
                    val newCallExpression = functionLiteralArgument.moveInsideParenthesesAndReplaceWith(replacement, lambdaArgumentName)
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
            container: PsiElement,
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
                    append(" = ")
                    append(initializerText)
                }.let { psiFactory.createProperty(it) }
            }
            val lambdaArgumentName = if (expression.isLambdaOutsideParentheses()) {
                analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                    getLambdaArgumentNameIfShouldBeNamed(expression.getStrictParentOfType<KtLambdaArgument>()!!)
                }
            } else null
            var anchor = calculateAnchor(expression, container) ?: return
            val needBraces = container !is KtBlockExpression && container !is KtClassBody && container !is KtFile
            val shouldReplaceOccurrence = !needBraces && expression.shouldReplaceOccurrence(container)
            application.runWriteAction {
                if (!needBraces) {
                    property = container.addBefore(property, anchor) as KtDeclaration
                    container.addBefore(psiFactory.createNewLine(), anchor)
                } else {
                    var emptyBody: KtExpression = psiFactory.createEmptyBody()
                    val firstChild = emptyBody.firstChild
                    emptyBody.addAfter(psiFactory.createNewLine(), firstChild)

                    if (replaceOccurrence) {
                        val exprAfterReplace = replaceExpression(expression, addToReferences = false, lambdaArgumentName)
                        exprAfterReplace.isOccurrence = true
                        if (anchor == expression) {
                            anchor = exprAfterReplace
                        }

                        var oldElement: PsiElement = container
                        if (container is KtWhenEntry) {
                            val body = container.expression
                            if (body != null) {
                                oldElement = body
                            }
                        } else if (container is KtContainerNode) {
                            val children = container.children
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
                    if (container is KtContainerNode) {
                        if (container.parent is KtIfExpression) {
                            val next = container.nextSibling
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
                    if (shouldReplaceOccurrence) {
                        replaceExpression(expression, addToReferences = true, lambdaArgumentName)
                    } else {
                        val sibling = PsiTreeUtil.skipSiblingsBackward(expression, PsiWhiteSpace::class.java)
                        if (sibling == property) {
                            expression.parent.deleteChildRange(property.nextSibling, expression)
                        } else {
                            expression.delete()
                        }
                    }
                }
                introducedVariablePointer = property.createSmartPointer()
            }

            specifyTypeIfNeeded(property)
        }

        context(KtAnalysisSession)
        private fun getLambdaArgumentNameIfShouldBeNamed(argument: KtLambdaArgument): Name? {
            return if (shouldLambdaParameterBeNamed(argument)) {
                NamedArgumentUtils.getStableNameFor(argument)
            } else null
        }

        private fun specifyTypeIfNeeded(declaration: KtDeclaration) {
            if (declaration !is KtProperty) return
            assert(declaration.typeReference == null)
            analyzeIfExplicitTypeOrArgumentsAreNeeded(declaration)
            if (KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY || mustSpecifyTypeExplicitly) {
                application.runWriteAction {
                    declaration.typeReference = psiFactory.createType(expressionRenderedType)
                    declaration.typeReference?.let { shortenReferences(it) }
                }
            }
        }

        private fun analyzeIfExplicitTypeOrArgumentsAreNeeded(property: KtProperty) {
            val initializer = property.initializer ?: return
            val propertyRenderedType = analyzeInModalWindow(property, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                property.getReturnKtType().render(position = Variance.INVARIANT)
            }
            if (propertyRenderedType == expressionRenderedType) {
                renderedTypeArgumentsIfMightBeNeeded = null
            } else if (!areTypeArgumentsNeededForCorrectTypeInference(initializer)) {
                mustSpecifyTypeExplicitly = true
            }
        }

        fun runRefactoring(project: Project, editor: Editor?, isVar: Boolean) {
            if (container !is KtDeclarationWithBody) return runRefactoring(
                isVar,
                expression,
                container,
            )

            container.bodyExpression.sure { "Original body is not found: $container" }

            expression.putCopyableUserData(EXPRESSION_KEY, true)

            analyzeInModalWindow(container, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                ConvertToBlockBodyUtils.createContext(container, ShortenReferencesFacility.getInstance(), reformat = false)
            }?.let { context ->
                application.runWriteAction {
                    ConvertToBlockBodyUtils.convert(container, context)
                }
            }

            val newContainer = container.bodyBlockExpression ?: return showErrorHint(
                project,
                editor,
                KotlinBundle.message("cannot.refactor.not.expression")
            )

            val newExpression = newContainer.findExpressionByCopyableDataAndClearIt(EXPRESSION_KEY)

            runRefactoring(
                isVar,
                newExpression ?: return,
                newContainer,
            )
        }
    }

    private fun calculateAnchor(expression: PsiElement, container: PsiElement): PsiElement? {
        if (expression != container) return expression.parentsWithSelf.firstOrNull { it.parent == container }
        val startOffset = min(container.endOffset, expression.startOffset)
        return container.allChildren.lastOrNull { it.textRange.contains(startOffset) }
    }

    private fun KtExpression.shouldReplaceOccurrence(container: PsiElement?): Boolean {
        val effectiveParent = (parent as? KtScriptInitializer)?.parent ?: parent
        return container != effectiveParent || analyzeInModalWindow(this, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            isUsedAsExpression()
        }
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

    override fun doRefactoringWithSelectedTargetContainer(
        project: Project,
        editor: Editor?,
        expression: KtExpression,
        containers: Containers,
        isVar: Boolean,
        occurrencesToReplace: List<KtExpression>?,
        onNonInteractiveFinish: ((KtDeclaration) -> Unit)?
    ) {
        if (!isRefactoringApplicableByPsi(project, editor, expression)) return

        val expressionRenderedType = analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val expressionType = expression.getKtType()
            if (expressionType != null && expressionType.isUnit) return@analyzeInModalWindow null
            (expressionType ?: builtinTypes.ANY).render(position = Variance.INVARIANT)
        } ?: return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.expression.has.unit.type"))

        val renderedTypeArguments = expression.getPossiblyQualifiedCallExpression()?.let { callExpression ->
            analyzeInModalWindow(callExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                getRenderedTypeArguments(callExpression)
            }
        }

        val isInplaceAvailable = editor != null

        val callback = {
            val replaceOccurrence = expression.shouldReplaceOccurrence(containers.targetContainer)

            expression.chooseApplicableComponentNamesForVariableDeclaration(replaceOccurrence, editor) { componentNames ->
                val anchor = calculateAnchor(expression, containers.targetContainer) as? KtElement
                    ?: return@chooseApplicableComponentNamesForVariableDeclaration
                val suggestedNames = analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                    val nameValidator = KotlinDeclarationNameValidator(
                        anchor,
                        true,
                        KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                        this,
                    )
                    if (componentNames.isNotEmpty()) {
                        componentNames.map { componentName -> suggestNameByName(componentName, nameValidator).let(::listOf) }
                    } else {
                        with(KotlinNameSuggester()) {
                            suggestExpressionNames(expression, nameValidator).toList()
                        }.let(::listOf)
                    }
                }

                val introduceVariableContext = IntroduceVariableContext(
                    expression,
                    suggestedNames,
                    containers.targetContainer,
                    replaceOccurrence,
                    expressionRenderedType,
                    componentNames,
                    renderedTypeArguments,
                )

                project.executeCommand(INTRODUCE_VARIABLE, null) {
                    introduceVariableContext.runRefactoring(project, editor, isVar)

                    val property = introduceVariableContext.introducedVariablePointer?.element ?: return@executeCommand

                    if (editor == null) {
                        return@executeCommand
                    }

                    editor.caretModel.moveToOffset(property.textOffset)
                    editor.selectionModel.removeSelection()

                    fun postProcess(declaration: KtDeclaration, editor: Editor?) {
                        if (renderedTypeArguments != null) {
                            val initializer = when (declaration) {
                                is KtProperty -> declaration.initializer
                                is KtDestructuringDeclaration -> declaration.initializer
                                else -> null
                            } ?: return
                            if (introduceVariableContext.renderedTypeArgumentsIfMightBeNeeded != null &&
                                !KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY
                            ) {
                                initializer.getPossiblyQualifiedCallExpression()?.let { callExpression ->
                                    application.runWriteAction { addTypeArguments(callExpression, renderedTypeArguments, project) }
                                }
                            }
                        }

                        if (editor != null && !replaceOccurrence) {
                            editor.caretModel.moveToOffset(declaration.endOffset)
                        }
                    }

                    if (!isInplaceAvailable) {
                        postProcess(property, editor)
                        return@executeCommand
                    }

                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

                    when (property) {
                        is KtProperty -> {
                            KotlinVariableInplaceIntroducer(
                                property,
                                introduceVariableContext.reference?.element,
                                introduceVariableContext.references.mapNotNull { it.element }.toTypedArray(),
                                suggestedNames.single(),
                                expressionRenderedType,
                                introduceVariableContext.mustSpecifyTypeExplicitly,
                                title = INTRODUCE_VARIABLE,
                                project,
                                editor,
                                ::postProcess,
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
        if (isInplaceAvailable && !isUnitTestMode()) {
            application.invokeLater {
                SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use { callback() }
            }
        } else {
            callback()
        }
    }

    private fun areTypeArgumentsNeededForCorrectTypeInference(expression: KtExpression): Boolean {
        val call = expression.getPossiblyQualifiedCallExpression() ?: return false
        if (call.typeArgumentList != null) return false
        val callee = call.calleeExpression ?: return false
        val diagnostics = analyzeInModalWindow(callee, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            callee.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        }
        return (diagnostics.any { diagnostic -> diagnostic is KtFirDiagnostic.NewInferenceNoInformationForParameter })
    }

    override fun filterContainersWithContainedLambdasByAnalyze(
        containersWithContainedLambdas: Sequence<ContainerWithContained>,
        physicalExpression: KtExpression,
        referencesFromExpressionToExtract: List<KtReferenceExpression>,
    ): Sequence<ContainerWithContained> {
        return analyzeInModalWindow(physicalExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val psiToCheck = referencesFromExpressionToExtract.mapNotNull { reference ->
                // in case of an unresolved reference consider all containers applicable
                val symbol = reference.mainReference.resolveToSymbol() ?: return@mapNotNull null

                if (symbol.origin == KtSymbolOrigin.SOURCE) {
                    symbol.psi
                } else if (symbol is KtValueParameterSymbol && symbol.isImplicitLambdaParameter) {
                    (symbol.getContainingSymbol() as? KtAnonymousFunctionSymbol)?.psi as? KtFunctionLiteral
                } else null
            }

            containersWithContainedLambdas.takeWhile { (container, contained) ->
                // `contained` is among parents of expression to extract;
                // if reference is resolved and its psi is not inside `contained` then it will be accessible next to `contained`
                psiToCheck.all { psi -> !psi.isInsideOf(listOf(contained)) }
            }
        }
    }
}
