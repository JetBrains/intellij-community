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
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
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
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableContext
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper.Containers
import org.jetbrains.kotlin.idea.refactoring.introduce.calculateAnchorForExpressions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.substringContextOrThis
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

object K2IntroduceVariableHandler : KotlinIntroduceVariableHandler() {
    private class IntroduceVariableContext(
        project: Project,
        isVar: Boolean,
        nameSuggestions: List<Collection<String>>,
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

        @OptIn(KtAllowAnalysisOnEdt::class)
        override fun KtLambdaArgument.getLambdaArgumentNameByAnalyze(): Name? = allowAnalysisOnEdt {
            @OptIn(KtAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(this) {
                    NamedArgumentUtils.getStableNameFor(this@getLambdaArgumentNameByAnalyze)
                }
            }
        }

        private fun analyzeIfExplicitTypeOrArgumentsAreNeeded(property: KtProperty) {
            val initializer = property.initializer ?: return

            @OptIn(KtAllowAnalysisOnEdt::class)
            allowAnalysisOnEdt {
                @OptIn(KtAllowAnalysisFromWriteAction::class)
                allowAnalysisFromWriteAction {
                    analyze(property) {
                        val propertyRenderedType = property.getReturnKtType().render(position = Variance.INVARIANT)
                        if (propertyRenderedType == expressionRenderedType) {
                            renderedTypeArgumentsIfMightBeNeeded = null
                        } else if (!areTypeArgumentsNeededForCorrectTypeInference(initializer)) {
                            mustSpecifyTypeExplicitly = true
                        }
                    }
                }
            }
        }

        override fun convertToBlockBodyAndSpecifyReturnTypeByAnalyze(declaration: KtDeclarationWithBody): KtDeclarationWithBody {
            @OptIn(KtAllowAnalysisOnEdt::class)
            val convertToBlockBodyContext = allowAnalysisOnEdt {
                @OptIn(KtAllowAnalysisFromWriteAction::class)
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

        val allOccurrences = listOf(expression) // TODO: KTIJ-27861
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

            expression.chooseApplicableComponentNamesForVariableDeclaration(
                haveOccurrencesToReplace = replaceFirstOccurrence || allReplaces.size > 1,
                editor,
            ) { componentNames ->
                val anchor = calculateAnchorForExpressions(commonParent, commonContainer, allReplaces)
                    ?: return@chooseApplicableComponentNamesForVariableDeclaration
                val isDestructuringDeclaration = componentNames.isNotEmpty()
                val suggestedNames = analyzeInModalWindow(expression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                    val nameValidator = KotlinDeclarationNameValidator(
                        anchor,
                        true,
                        KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                        this,
                    )
                    if (isDestructuringDeclaration) {
                        componentNames.map { componentName -> suggestNameByName(componentName, nameValidator).let(::listOf) }
                    } else {
                        with(KotlinNameSuggester()) {
                            suggestExpressionNames(expression, nameValidator).toList()
                        }.let(::listOf)
                    }
                }

                val introduceVariableContext = IntroduceVariableContext(
                    project,
                    isVar,
                    suggestedNames,
                    replaceFirstOccurrence,
                    isDestructuringDeclaration,
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
