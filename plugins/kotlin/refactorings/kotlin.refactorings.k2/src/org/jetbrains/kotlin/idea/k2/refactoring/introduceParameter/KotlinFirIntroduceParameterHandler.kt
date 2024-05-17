// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer
import com.intellij.usageView.UsageInfo
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFunctionalTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider.ValidatorTarget
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.approximateWithResolvableType
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*


class KotlinFirIntroduceParameterHandler(private val helper: KotlinIntroduceParameterHelper<KtNamedDeclaration> = KotlinIntroduceParameterHelper.Default()) : RefactoringActionHandler {

    context(KtAnalysisSession)
    private fun findInternalUsagesOfParametersAndReceiver(
        targetParent: KtNamedDeclaration
    ): MultiMap<KtElement, KtElement> {
        val usages = MultiMap<KtElement, KtElement>()

        targetParent.getValueParameters()
            .filter { !it.hasValOrVar() }
            .forEach {
                val paramUsages = ReferencesSearch.search(it).map { reference -> reference.element as KtElement }
                if (paramUsages.isNotEmpty()) {
                    usages.put(it, paramUsages)
                }
            }


        val receiverTypeRef = (targetParent as? KtFunction)?.receiverTypeReference
        if (receiverTypeRef != null) {
            targetParent.acceptChildren(
                object : KtTreeVisitorVoid() {
                    override fun visitThisExpression(expression: KtThisExpression) {
                        super.visitThisExpression(expression)
                        if (expression.instanceReference.mainReference.resolve() == targetParent) {
                            usages.putValue(receiverTypeRef, expression)
                        }
                    }

                    override fun visitKtElement(element: KtElement) {
                        super.visitKtElement(element)

                        val symbol = element.resolveCall()?.successfulCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                        val callableSymbol = targetParent.getSymbol() as? KtCallableSymbol
                        if (callableSymbol != null) {
                            if ((symbol?.dispatchReceiver as? KtImplicitReceiverValue)?.symbol == callableSymbol.receiverParameter || (symbol?.extensionReceiver as? KtImplicitReceiverValue)?.symbol == callableSymbol.receiverParameter) {
                                usages.putValue(receiverTypeRef, element)
                            }
                        }
                    }
                }
            )
        }
        return usages
    }


    context(KtAnalysisSession)
    private fun getExpressionType(
        physicalExpression: KtExpression,
        expression: KtExpression
    ): KtType? {
        val type = if (physicalExpression is KtProperty && physicalExpression.isLocal) {
            physicalExpression.getReturnKtType()
        } else {
            (expression.extractableSubstringInfo as? K2ExtractableSubstringInfo)?.guessLiteralType() ?: physicalExpression.getKtType()
        }
        return approximateWithResolvableType(type, physicalExpression)
    }

    operator fun invoke(project: Project, editor: Editor, expression: KtExpression, targetParent: KtNamedDeclaration) {
        val expressionTypeEvaluator: KtAnalysisSession.() -> KtType? = {
            val physicalExpression = expression.substringContextOrThis
            getExpressionType(physicalExpression, expression)
        }
        val nameSuggester: KtAnalysisSession.(KtType) -> List<String> = { expressionType ->
            val suggestedNames = SmartList<String>()
            val physicalExpression = expression.substringContextOrThis
            val body = when (targetParent) {
                is KtFunction -> targetParent.bodyExpression
                is KtClass -> targetParent.body
                else -> null
            }
            val bodyValidator: ((String) -> Boolean) =
                body?.let { b ->
                    { name: String ->
                        KotlinDeclarationNameValidator(b, true, ValidatorTarget.PARAMETER).validate(name)
                    }
                } ?: { true }
            val nameValidator = CollectingNameValidator(targetParent.getValueParameters().mapNotNull { it.name }, bodyValidator)

            if (physicalExpression is KtProperty && !isUnitTestMode()) {
                suggestedNames.addIfNotNull(physicalExpression.name)
            }
            suggestedNames.addAll(KotlinNameSuggester.suggestNamesByType(expressionType, targetParent, nameValidator, "p"))
            suggestedNames
        }
        addParameter(project, editor, expression, targetParent, expressionTypeEvaluator, nameSuggester)
    }

    /**
     * run change signature refactoring, just like [invoke], but with configurable expression type, and name
     * (to be reused in "create parameter from usage" where both type and name are fixed, and computed a bit differently from the regular "introduce parameter")
     */
    fun addParameter(project: Project, editor: Editor, expression: KtExpression, targetParent: KtNamedDeclaration, expressionTypeEvaluator: KtAnalysisSession.()->KtType?, nameSuggester:  KtAnalysisSession.(KtType)->List<String>) {
        val physicalExpression = expression.substringContextOrThis
        if (physicalExpression is KtProperty && physicalExpression.isLocal && physicalExpression.nameIdentifier == null) {
            showErrorHintByKey(project, editor, "cannot.refactor.no.expression", INTRODUCE_PARAMETER)
            return
        }

        var message: String? = null
        var suggestedNames: List<String> = listOf()
        val descriptorToType = analyzeInModalWindow(targetParent, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val expressionType = expressionTypeEvaluator.invoke(this)
            message = if (expressionType == null) {
                KotlinBundle.message("error.text.expression.has.no.type")
            } else if (expressionType.isUnit || expressionType.isNothing) {
                KotlinBundle.message(
                    "cannot.introduce.parameter.of.0.type",
                    expressionType.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT),
                )
            } else null

            if (message != null) {
                return@analyzeInModalWindow null
            }
            require (expressionType!=null)
            suggestedNames = nameSuggester.invoke(this, expressionType)

            val parametersUsages = findInternalUsagesOfParametersAndReceiver(targetParent)

            val forbiddenRanges = (targetParent as? KtClass)?.declarations?.asSequence()
                ?.filter(::isObjectOrNonInnerClass)
                ?.map { it.textRange }
                ?.toList()
                ?: Collections.emptyList()

            val occurrencesToReplace = if (expression is KtProperty) {
                ReferencesSearch.search(expression).mapNotNullTo(SmartList(expression.toRange())) { it.element.toRange() }
            } else {
                K2SemanticMatcher.findMatches(patternElement = expression, scopeElement = targetParent)
                    .filterNot {
                        val textRange = it.textRange
                        forbiddenRanges.any { range -> range.intersects(textRange) }
                    }
                    .mapNotNull { match ->
                        when (match) {
                            is KtExpression -> match
                            is KtStringTemplateEntryWithExpression -> match.expression
                            else -> null
                        }?.toRange()
                    }
            }

            val psiFactory = KtPsiFactory(project)
            introduceParameterDescriptor(
                KtPsiUtil.safeDeparenthesize(expression),
                targetParent,
                suggestedNames,
                physicalExpression,
                expressionType,
                parametersUsages,
                occurrencesToReplace,
                psiFactory
            ) to expressionType
        }

        message?.let {
            showErrorHint(project, editor, it, INTRODUCE_PARAMETER)
            return
        }

        if (descriptorToType == null) {
            return
        }

        val (introduceParameterDescriptor, replacementType) = descriptorToType

        project.executeCommand(
            INTRODUCE_PARAMETER,
            null,
            fun() {
                val isTestMode = isUnitTestMode()
                val haveLambdaArgumentsToReplace = introduceParameterDescriptor.occurrencesToReplace.any { range ->
                    range.elements.any { it is KtLambdaExpression && it.parent is KtLambdaArgument }
                }
                val inplaceIsAvailable = editor.settings.isVariableInplaceRenameEnabled
                        && !isTestMode
                        && !haveLambdaArgumentsToReplace
                        && expression.extractableSubstringInfo == null
                        && !expression.mustBeParenthesizedInInitializerPosition()

                if (isTestMode) {
                    introduceParameterDescriptor.performRefactoring()
                    return
                }

                if (inplaceIsAvailable) {

                    val introducer = object : KotlinInplaceParameterIntroducerBase<KtType, KtNamedDeclaration>(
                        introduceParameterDescriptor,
                        replacementType,
                        suggestedNames.toTypedArray(),
                        project,
                        editor
                    ) {
                        override fun performRefactoring(descriptor: IntroduceParameterDescriptor<KtNamedDeclaration>) {
                            descriptor.performRefactoring()
                        }

                        override fun switchToDialogUI() {
                            // TODO switch to dialog UI KTIJ-29637
                            //everything is invalidated after stopIntroduce call so, one need to start from scratch
                            //stopIntroduce(myEditor)
                            //showDialog(project, editor, physicalExpression, replacementType, suggestedNames, introduceParameterDescriptor)
                        }
                    }
                    if (introducer.startInplaceIntroduceTemplate()) return
                }

                showDialog(project, editor, physicalExpression, replacementType, suggestedNames, introduceParameterDescriptor)
            }
        )
    }

    private fun showDialog(
        project: Project,
        editor: Editor,
        physicalExpression: KtExpression,
        replacementType: KtType,
        suggestedNames: List<String>,
        introduceParameterDescriptor: IntroduceParameterDescriptor<KtNamedDeclaration>
    ) {
        val types = analyzeInModalWindow(physicalExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            buildList {
                add(replacementType.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT))
                replacementType.getAllSuperTypes(true).mapTo(this) {
                    it.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
                }
            }
        }
        KotlinIntroduceParameterDialog(
            project,
            editor,
            introduceParameterDescriptor,
            suggestedNames.toTypedArray(),
            types,
            helper
        ).show()
    }

    private fun introduceParameterDescriptor(
        originalExpression: KtExpression,
        targetParent: KtNamedDeclaration,
        suggestedNames: List<String>,
        physicalExpression: KtExpression,
        replacementType: KtType,
        parametersUsages: MultiMap<KtElement, KtElement>,
        occurrencesToReplace: List<KotlinPsiRange>,
        psiFactory: KtPsiFactory
    ): IntroduceParameterDescriptor<KtNamedDeclaration> = helper.configure(
        IntroduceParameterDescriptor(
            originalRange = originalExpression.toRange(),
            callable = targetParent,
            callableDescriptor = targetParent,
            newParameterName = suggestedNames.first().quoteIfNeeded(),
            newParameterTypeText = analyze(physicalExpression) {
                replacementType.render(KtTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
                    functionalTypeRenderer = KaFunctionalTypeRenderer.AS_FUNCTIONAL_TYPE
                }, position = Variance.IN_VARIANCE)
            },
            argumentValue = originalExpression,
            withDefaultValue = false,
            parametersUsages = parametersUsages,
            occurrencesToReplace = occurrencesToReplace,
            occurrenceReplacer = replacer@{
                val expressionToReplace = it.elements.single() as KtExpression
                val replacingExpression = psiFactory.createExpression(newParameterName)
                val substringInfo = expressionToReplace.extractableSubstringInfo
                val result = when {
                    expressionToReplace is KtProperty -> return@replacer expressionToReplace.delete()
                    expressionToReplace.isLambdaOutsideParentheses() -> {
                        val lambdaArgument = expressionToReplace
                            .getStrictParentOfType<KtLambdaArgument>()!!
                        val lambdaArgumentName = if (shouldLambdaParameterBeNamed(lambdaArgument)) {
                            analyze(lambdaArgument) { NamedArgumentUtils.getStableNameFor(lambdaArgument) }
                        } else null
                        lambdaArgument
                            .moveInsideParenthesesAndReplaceWith(replacingExpression, lambdaArgumentName)
                    }

                    substringInfo != null -> substringInfo.replaceWith(replacingExpression)
                    else -> expressionToReplace.replaced(replacingExpression)
                }
                result.removeTemplateEntryBracesIfPossible()
            }
        )
    )

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        (AbstractInplaceIntroducer.getActiveIntroducer(editor) as? KotlinInplaceParameterIntroducerBase<*, *>)?.let {
            it.switchToDialogUI()
            return
        }

        if (file !is KtFile) return

        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        if (elementAtCaret.getNonStrictParentOfType<KtAnnotationEntry>() != null) {
            showErrorHint(
                project,
                editor,
                KotlinBundle.message("error.text.introduce.parameter.is.not.available.inside.of.annotation.entries"),
                INTRODUCE_PARAMETER
            )
            return
        }
        if (elementAtCaret.getNonStrictParentOfType<KtParameter>() != null) {
            showErrorHint(
                project,
                editor,
                KotlinBundle.message("error.text.introduce.parameter.is.not.available.for.default.value"),
                INTRODUCE_PARAMETER
            )
            return
        }

        selectNewParameterContext(editor, file) { elements, targetParent ->
            val expression = ((elements.singleOrNull() as? KtBlockExpression)?.statements ?: elements).singleOrNull()
            if (expression is KtExpression) {
                invoke(project, editor, expression, targetParent as KtNamedDeclaration)
            } else {
                showErrorHintByKey(project, editor, "cannot.refactor.no.expression", INTRODUCE_PARAMETER)
            }
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$INTRODUCE_PARAMETER can only be invoked from editor")
    }
}

fun IntroduceParameterDescriptor<KtNamedDeclaration>.performRefactoring(onExit: (() -> Unit)? = null) {
    val superMethods = checkSuperMethods(callable, emptyList(), RefactoringBundle.message("to.refactor"))
    val targetCallable = superMethods.filterIsInstance<KtNamedDeclaration>().firstOrNull() ?: return

    val methodDescriptor = KotlinMethodDescriptor((targetCallable as? KtClass)?.primaryConstructor ?: targetCallable)
    val changeInfo = KotlinChangeInfo(methodDescriptor)

    val defaultValue = if (newArgumentValue is KtProperty) (newArgumentValue as KtProperty).initializer else newArgumentValue

    if (!withDefaultValue) {
        val parameters = targetCallable.getValueParameters()
        val withReceiver = methodDescriptor.receiver != null
        parametersToRemove
            .map {
                if (it is KtParameter) {
                    parameters.indexOf(it) + if (withReceiver) 1 else 0
                } else 0
            }
            .sortedDescending()
            .forEach { changeInfo.removeParameter(it) }
        //parametersToRemove already checked if it's possible
        changeInfo.checkUsedParameters = false
    }

    val parameterInfo = KotlinParameterInfo(
        originalType = KotlinTypeInfo(newParameterTypeText, targetCallable),
        name = newParameterName,
        originalIndex = -1,
        valOrVar = valVar,
        defaultValueForCall = defaultValue,
        defaultValueAsDefaultParameter = withDefaultValue,
        defaultValue = if (withDefaultValue) defaultValue else null,
        context = targetCallable
    )
    changeInfo.addParameter(parameterInfo)

    object : KotlinChangeSignatureProcessor(targetCallable.project, changeInfo) {
        override fun performRefactoring(usages: Array<out UsageInfo?>) {
            super.performRefactoring(usages)
            occurrencesToReplace.forEach {
                occurrenceReplacer(it)
            }
            onExit?.invoke()
        }
    }.run()
}