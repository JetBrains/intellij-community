// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.util.application
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getImplicitReceivers
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper.Containers
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

typealias SuggestedNames = List<Collection<String>>

object K2IntroduceVariableHandler : KotlinIntroduceVariableHandler() {
    private class IntroduceVariableContext(
        project: Project,
        isVar: Boolean,
        nameSuggestions: SuggestedNames,
        replaceFirstOccurrence: Boolean,
        isDestructuringDeclaration: Boolean,
        private val expressionRenderedType: String,
        var renderedTypeArgumentsIfMightBeNeeded: String?,
    ) : KotlinIntroduceVariableContext(project, isVar, nameSuggestions, replaceFirstOccurrence, isDestructuringDeclaration) {
        var mustSpecifyTypeExplicitly = false

        override fun analyzeDeclarationAndSpecifyTypeIfNeeded(declaration: KtDeclaration) {
            if (declaration !is KtProperty) return
            assert(declaration.typeReference == null)
            analyzeIfExplicitTypeOrArgumentsAreNeeded(declaration)
            if (KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY || mustSpecifyTypeExplicitly) {
                declaration.typeReference = psiFactory.createType(expressionRenderedType)
                declaration.typeReference?.let { shortenReferences(it) }
            }
        }

        @OptIn(KaAllowAnalysisOnEdt::class)
        override fun KtLambdaArgument.getLambdaArgumentNameByAnalyze(): Name? = allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(this) {
                    NamedArgumentUtils.getStableNameFor(this@getLambdaArgumentNameByAnalyze)
                }
            }
        }

        /**
         * A type must be specified when introducing variables in two cases.
         *
         * The first case is when a lambda expression is extracted:
         * ```kotlin
         * fun foo(nums: List<Int>) {
         *     nums.find <selection>{ it == 42 }</selection>
         * }
         * ```
         * See the following tests:
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.Uncategorized.testIntroduceLambdaAndCreateBlock]
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.Uncategorized.testIntroduceLambdaAndCreateBlock2]
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.Uncategorized.testFunctionLiteralFromExpected]
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.Uncategorized.testFunctionLiteralWithExtraArgs]
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.Uncategorized.testFunctionLiteral]
         *
         * The second case when a type must be specified is for anonymous objects
         * that implement more than one supertype and are extracted into non-local variables:
         * ```kotlin
         * interface A
         * interface B
         *
         * class Main {
         *     val a: A = <selection>object : A, B {}</selection>
         * }
         * ```
         * If the 'Introduce Variable' refactoring had not specified a type for the code above,
         * it would lead to the `AMBIGUOUS_ANONYMOUS_TYPE_INFERRED` compiler error.
         *
         * See the following tests:
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.AnonymousObjects.testClassPropertyAmbiguous]
         * + [org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableTestGenerated.AnonymousObjects.testDefaultParamNonLocalAmbiguous]
         *
         * Type arguments must be specified when the user does not choose the 'Specify type explicitly'
         * option, for instance, in the following case:
         * ```kotlin
         * // before
         * val v : List<List<Int>> = mutableListOf(<selection>listOf()</selection>)
         * ```
         * ```kotlin
         * // after
         * val elements = listOf<Int>()
         * val v : List<List<Int>> = mutableListOf(elements)
         * ```
         * However, when the user specifies the type explicitly, the redundant type arguments are omitted:
         * ```kotlin
         * // after
         * val elements: List<Int> = listOf()
         * val v : List<List<Int>> = mutableListOf(elements))
         * ```
         */
        @OptIn(KaExperimentalApi::class)
        private fun analyzeIfExplicitTypeOrArgumentsAreNeeded(property: KtProperty) {
            val initializer = property.initializer ?: return

            @OptIn(KaAllowAnalysisOnEdt::class)
            allowAnalysisOnEdt {
                @OptIn(KaAllowAnalysisFromWriteAction::class)
                allowAnalysisFromWriteAction {
                    analyze(property) {
                        if (initializer is KtObjectLiteralExpression &&
                            property.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).any {
                                it is KaFirDiagnostic.AmbiguousAnonymousTypeInferred
                            }
                        ) {
                            mustSpecifyTypeExplicitly = true
                        } else if (property.returnType.render(position = Variance.INVARIANT) == expressionRenderedType) {
                            renderedTypeArgumentsIfMightBeNeeded = null
                        } else if (initializer is KtLambdaExpression && !areTypeArgumentsNeededForCorrectTypeInference(initializer)) {
                            mustSpecifyTypeExplicitly = true
                        }
                    }
                }
            }
        }

        override fun convertToBlockBodyAndSpecifyReturnTypeByAnalyze(declaration: KtDeclarationWithBody): KtDeclarationWithBody {
            @OptIn(KaAllowAnalysisOnEdt::class)
            val convertToBlockBodyContext = allowAnalysisOnEdt {
                @OptIn(KaAllowAnalysisFromWriteAction::class)
                allowAnalysisFromWriteAction {
                    analyze(declaration) {
                        ConvertToBlockBodyUtils.createContext(
                            declaration,
                            ShortenReferencesFacility.getInstance(),
                            reformat = false,
                            isErrorReturnTypeAllowed = true,
                        )
                    }
                }
            } ?: errorWithAttachment("Failed to create context for converting expression body to block body") {
                withPsiEntry("declaration", declaration)
            }

            ConvertToBlockBodyUtils.convert(declaration, convertToBlockBodyContext)

            return declaration
        }
    }

    private fun KtExpression.shouldReplaceOccurrence(container: PsiElement?): Boolean {
        val effectiveParent = (parent as? KtScriptInitializer)?.parent ?: parent
        return container != effectiveParent || analyzeInModalWindow(this, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            isUsedAsExpression
        }
    }

    private fun KtExpression.chooseDestructuringNames(
        editor: Editor?,
        haveOccurrencesToReplace: Boolean,
        nameValidator: KotlinDeclarationNameValidator,
        callback: (SuggestedNames) -> Unit,
    ) {
        if (haveOccurrencesToReplace) return callback(emptyList())
        return chooseDestructuringNames(
            editor = editor,
            expression = this,
            nameValidator = nameValidator,
            callback = callback
        )
    }

    private fun executeMultiDeclarationTemplate(
        project: Project,
        editor: Editor,
        declaration: KtDestructuringDeclaration,
        suggestedNames: SuggestedNames,
    ) {
        StartMarkAction.canStart(editor)?.let { return }

        val builder = TemplateBuilderImpl(declaration)
        for ((entry, names) in declaration.entries.zip(suggestedNames)) {
            val templateExpression = object : Expression() {

                private val lookupItems: Array<LookupElementBuilder> = names
                    .map { LookupElementBuilder.create(it) }
                    .toTypedArray()

                override fun calculateResult(context: ExpressionContext): TextResult =
                    TextResult(names.first())

                override fun calculateLookupItems(context: ExpressionContext): Array<LookupElementBuilder> =
                    lookupItems
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

    @OptIn(KaExperimentalApi::class)
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
            val substringInfo = expression.extractableSubstringInfo as? K2ExtractableSubstringInfo
            val physicalExpression = expression.substringContextOrThis

            val expressionType = substringInfo?.guessLiteralType() ?: calculateExpectedType(physicalExpression)
            if (expressionType != null && expressionType.isUnitType) return@analyzeInModalWindow null
            (expressionType ?: builtinTypes.any).render(position = Variance.INVARIANT)
        } ?: return showErrorHint(project, editor, KotlinBundle.message("cannot.refactor.expression.has.unit.type"))

        val renderedTypeArguments = expression.getPossiblyQualifiedCallExpression()?.let { callExpression ->
            analyzeInModalWindow(callExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                getRenderedTypeArguments(callExpression)
            }
        }

        val isInplaceAvailable = isInplaceAvailable(editor, project)

        val allOccurrences = occurrencesToReplace ?: expression.findOccurrences(containers.occurrenceContainer)

        val callback = Pass.create { replaceChoice: OccurrencesChooser.ReplaceChoice ->
            val allReplaces = when (replaceChoice) {
                OccurrencesChooser.ReplaceChoice.ALL -> allOccurrences
                else -> listOf(expression)
            }
            val commonParent = PsiTreeUtil.findCommonParent(allReplaces.map { it.substringContextOrThis }) as KtElement

            var commonContainer = commonParent as? KtFile
                ?: commonParent.getContainer()
                ?: errorWithAttachment("Failed to find container for parent") { withPsiEntry("parent", commonParent) }

            if (commonContainer != containers.targetContainer && containers.targetContainer.isAncestor(commonContainer, true)) {
                commonContainer = containers.targetContainer
            }
            val replaceFirstOccurrence = allReplaces.firstOrNull()?.shouldReplaceOccurrence(commonContainer) == true

            val anchor = calculateAnchorForExpressions(commonParent, commonContainer, allReplaces) ?: return@create
            val nameValidator = KotlinDeclarationNameValidator(
                visibleDeclarationsContext = anchor,
                checkVisibleDeclarationsContext = true,
                target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
            )

            expression.chooseDestructuringNames(
                editor = editor,
                haveOccurrencesToReplace = replaceFirstOccurrence || allReplaces.size > 1,
                nameValidator = nameValidator,
            ) { destructuringNames ->
                val suggestedNames = destructuringNames.takeIf { it.isNotEmpty() } ?: suggestSingleVariableNames(expression, nameValidator)

                val introduceVariableContext = IntroduceVariableContext(
                    project,
                    isVar,
                    suggestedNames,
                    replaceFirstOccurrence,
                    destructuringNames.isNotEmpty(),
                    expressionRenderedType,
                    renderedTypeArguments,
                )

                project.executeCommand(INTRODUCE_VARIABLE, null) {
                    application.runWriteAction {
                        introduceVariableContext.convertToBlockIfNeededAndRunRefactoring(
                            expression,
                            commonContainer,
                            commonParent,
                            allReplaces
                        )
                    }

                    val property = introduceVariableContext.introducedVariablePointer?.element ?: return@executeCommand

                    if (editor == null) {
                        onNonInteractiveFinish?.invoke(property)
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

                        if (editor != null && !replaceFirstOccurrence) {
                            val endOffset = declaration.children.last { it !is PsiComment && it !is PsiWhiteSpace }.endOffset
                            editor.caretModel.moveToOffset(endOffset)
                        }
                    }

                    if (!isInplaceAvailable) {
                        postProcess(property, editor)
                        return@executeCommand
                    }

                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

                    when (property) {
                        is KtProperty -> {
                            val variableInplaceIntroducer = KotlinVariableInplaceIntroducer(
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
                            )
                            variableInplaceIntroducer.startInplaceIntroduceTemplate()
                        }

                        is KtDestructuringDeclaration -> {
                            executeMultiDeclarationTemplate(project, editor, property, suggestedNames)
                        }

                        else -> throw AssertionError("Unexpected declaration: ${property.getElementTextWithContext()}")
                    }
                }
            }

        }
        if (isInplaceAvailable && occurrencesToReplace == null && !isUnitTestMode()) {
            val chooser = object : OccurrencesChooser<KtExpression>(editor) {
                override fun getOccurrenceRange(occurrence: KtExpression): TextRange? {
                    return occurrence.extractableSubstringInfo?.contentRange ?: occurrence.textRange
                }
            }
            application.invokeLater {
                chooser.showChooser(expression, allOccurrences, callback)
            }
        } else {
            callback.accept(OccurrencesChooser.ReplaceChoice.ALL)
        }
    }

    override fun KtExpression.findOccurrences(occurrenceContainer: KtElement): List<KtExpression> =
        analyzeInModalWindow(contextElement = this, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            K2SemanticMatcher.findMatches(patternElement = this@findOccurrences, scopeElement = occurrenceContainer)
                .filterNot { it.isAssignmentLHS() }
                .mapNotNull { match ->
                    when (match) {
                        is KtExpression -> match
                        is KtStringTemplateEntryWithExpression -> match.expression
                        else -> errorWithAttachment("Unexpected candidate element ${match::class.java}") { withPsiEntry("match", match) }
                    }
                }
        }.ifEmpty { listOf(this) }

    @OptIn(KaExperimentalApi::class)
    private fun areTypeArgumentsNeededForCorrectTypeInference(expression: KtExpression): Boolean {
        val call = expression.getPossiblyQualifiedCallExpression() ?: return false
        if (call.typeArgumentList != null) return false
        val callee = call.calleeExpression ?: return false
        val diagnostics = analyzeInModalWindow(callee, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            callee.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        }
        return (diagnostics.any { diagnostic -> diagnostic is KaFirDiagnostic.NewInferenceNoInformationForParameter })
    }

    override fun filterContainersWithContainedLambdasByAnalyze(
        containersWithContainedLambdas: Sequence<ContainerWithContained>,
        physicalExpression: KtExpression,
        referencesFromExpressionToExtract: List<KtReferenceExpression>,
    ): Sequence<ContainerWithContained> {
        return analyzeInModalWindow(physicalExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val psiToCheck = referencesFromExpressionToExtract.flatMap { reference ->
                // in case of an unresolved reference consider all containers applicable
                val symbol = reference.mainReference.resolveToSymbol() ?: return@flatMap emptyList()
                val implicitReceivers = reference.resolveToCall()
                    ?.singleCallOrNull<KaCallableMemberCall<*, *>>()
                    ?.getImplicitReceivers()

                buildList {
                    implicitReceivers?.forEach { addIfNotNull(it.symbol.psi) }

                    if (symbol.origin == KaSymbolOrigin.SOURCE) {
                        addIfNotNull(symbol.psi)
                    } else if (symbol is KaValueParameterSymbol && symbol.isImplicitLambdaParameter) {
                        addIfNotNull(symbol.getFunctionLiteralByImplicitLambdaParameterSymbol())
                    }
                }
            }

            containersWithContainedLambdas.takeWhile { (_, contained) ->
                // `contained` is among parents of expression to extract;
                // if reference is resolved and its psi is not inside `contained` then it will be accessible next to `contained`
                psiToCheck.all { psi -> !psi.isInsideOf(listOf(contained)) }
            }
        }
    }
}

private fun KaSession.calculateExpectedType(expression: KtExpression): KaType? {
    if (expression is KtObjectLiteralExpression) {
        // Special handling for KtObjectLiteralExpression is required because an instance of
        // KaFirUsualClassType returned from the KaExpressionTypeProvider.getExpressionType
        // extension function is rendered as <anonymous>.
        // However, we can attempt to infer a denotable type for an anonymous object from the context
        // using the KaExpressionTypeProvider.getExpectedType extension function.
        val expectedType = expression.expectedType
        if (expectedType != null) return expectedType
        val parent = expression.parent
        when {
            // In certain cases, the KaExpressionTypeProvider.getExpectedType extension function
            // does not return an expected type.
            // See KT-67250
            parent is KtDelegatedSuperTypeEntry && parent.delegateExpression == expression -> return parent.typeReference?.type
            parent is KtParameter && parent.defaultValue == expression -> return parent.typeReference?.type
            else -> return expression.objectDeclaration.superTypeListEntries.firstOrNull()?.typeReference?.type ?: builtinTypes.any
        }
    }
    return expression.expressionType
}
