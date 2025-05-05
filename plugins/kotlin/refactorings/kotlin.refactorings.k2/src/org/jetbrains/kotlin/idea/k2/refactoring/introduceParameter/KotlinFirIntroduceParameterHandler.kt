// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter

import com.intellij.CommonBundle
import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFunctionalTypeRenderer
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
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
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.*
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionDataAnalyzer
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.approximateWithResolvableType
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionEngine
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*


open class KotlinFirIntroduceParameterHandler(private val helper: KotlinIntroduceParameterHelper<KtNamedDeclaration> = KotlinIntroduceParameterHelper.Default()) : RefactoringActionHandler {

    context(KaSession)
    protected fun findInternalUsagesOfParametersAndReceiver(
        targetParent: KtNamedDeclaration
    ): MultiMap<KtElement, KtElement> {
        val usages = MultiMap<KtElement, KtElement>()

        targetParent.getValueParameters()
            .filter { !it.hasValOrVar() }
            .forEach {
                val paramUsages = ReferencesSearch.search(it).asIterable().map { reference -> reference.element as KtElement }
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

                        val symbol = element.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                        val callableSymbol = targetParent.symbol as? KaCallableSymbol
                        if (callableSymbol != null) {
                            if ((symbol?.dispatchReceiver as? KaImplicitReceiverValue)?.symbol == callableSymbol.receiverParameter || (symbol?.extensionReceiver as? KaImplicitReceiverValue)?.symbol == callableSymbol.receiverParameter) {
                                usages.putValue(receiverTypeRef, element)
                            }
                        }
                    }
                }
            )
        }
        return usages
    }


    context(KaSession)
    private fun getExpressionType(
        physicalExpression: KtExpression,
        expression: KtExpression
    ): KaType? {
        val type = if (physicalExpression is KtProperty && physicalExpression.isLocal) {
            physicalExpression.returnType
        } else {
            (expression.extractableSubstringInfo as? K2ExtractableSubstringInfo)?.guessLiteralType() ?: physicalExpression.expressionType
        }
        return approximateWithResolvableType(type, physicalExpression)
    }

    open operator fun invoke(project: Project, editor: Editor, expression: KtExpression, targetParent: KtNamedDeclaration) {
        val expressionTypeEvaluator: KaSession.() -> KaType? = {
            val physicalExpression = expression.substringContextOrThis
            getExpressionType(physicalExpression, expression)
        }
        val nameSuggester: KaSession.(KaType) -> List<String> = { expressionType ->
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
                suggestedNames.addIfNotNull(physicalExpression.name?.quoteIfNeeded())
            }
            suggestedNames.addAll(KotlinNameSuggester.suggestNamesByType(expressionType, targetParent, nameValidator, "p"))
            suggestedNames
        }
        addParameter(project, editor, expression, expression, targetParent, expressionTypeEvaluator, nameSuggester)
    }

    /**
     * run change signature refactoring, just like [invoke], but with configurable expression type, and name
     * (to be reused in "create parameter from usage" where both type and name are fixed, and computed a bit differently from the regular "introduce parameter")
     */
    @OptIn(KaExperimentalApi::class)
    fun addParameter(
        project: Project,
        editor: Editor,
        expression: KtExpression,
        argumentValue: KtExpression?,
        targetParent: KtNamedDeclaration,
        expressionTypeEvaluator: KaSession.() -> KaType?,
        nameSuggester: KaSession.(KaType) -> List<String>,
        silently: Boolean = false,
    ) {
        val physicalExpression = expression.substringContextOrThis
        if (physicalExpression is KtProperty && physicalExpression.isLocal && physicalExpression.nameIdentifier == null) {
            showErrorHintByKey(project, editor, "cannot.refactor.no.expression", INTRODUCE_PARAMETER)
            return
        }

        var message: @Nls String? = null
        var suggestedNames: List<String> = listOf()
        val descriptorToType = analyzeInModalWindow(targetParent, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val expressionType = expressionTypeEvaluator.invoke(this)
            message = if (expressionType == null) {
                KotlinBundle.message("error.text.expression.has.no.type")
            } else if (expressionType.isUnitType || expressionType.isNothingType) {
                KotlinBundle.message(
                    "cannot.introduce.parameter.of.0.type",
                    expressionType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT),
                )
            } else null

            if (message != null) {
                return@analyzeInModalWindow null
            }
            require(expressionType != null)
            suggestedNames = nameSuggester.invoke(this, expressionType)

            val parametersUsages = findInternalUsagesOfParametersAndReceiver(targetParent)

            val forbiddenRanges = (targetParent as? KtClass)?.declarations?.asSequence()
                ?.filter(::isObjectOrNonInnerClass)
                ?.map { it.textRange }
                ?.toList()
                ?: Collections.emptyList()

            val occurrencesToReplace = if (expression is KtProperty) {
                ReferencesSearch.search(expression).asIterable().mapNotNullTo(SmartList(expression.toRange())) { it.element.toRange() }
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
                expression,
                targetParent,
                suggestedNames,
                physicalExpression,
                expressionType,
                parametersUsages,
                occurrencesToReplace,
                psiFactory,
                argumentValue
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
                        && PsiTreeUtil.isAncestor(introduceParameterDescriptor.callableDescriptor, physicalExpression, true)

                if (isTestMode || silently) {
                    introduceParameterDescriptor.performRefactoring(editor = editor)
                    return
                }

                if (inplaceIsAvailable) {

                    val introducer = object : KotlinInplaceParameterIntroducerBase<KaType, KtNamedDeclaration>(
                        introduceParameterDescriptor,
                        replacementType,
                        suggestedNames.toTypedArray(),
                        project,
                        editor
                    ) {
                        override fun performRefactoring(descriptor: IntroduceParameterDescriptor<KtNamedDeclaration>) {
                            descriptor.performRefactoring(editor = editor)
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

    @OptIn(KaExperimentalApi::class)
    private fun showDialog(
        project: Project,
        editor: Editor,
        physicalExpression: KtExpression,
        replacementType: KaType,
        suggestedNames: List<String>,
        introduceParameterDescriptor: IntroduceParameterDescriptor<KtNamedDeclaration>
    ) {
        val types = analyzeInModalWindow(physicalExpression, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            buildList {
                add(replacementType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT))
                replacementType.allSupertypes(shouldApproximate = true).mapTo(this) {
                    it.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
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

    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class, KaAllowAnalysisFromWriteAction::class)
    private fun introduceParameterDescriptor(
        originalExpression: KtExpression,
        targetParent: KtNamedDeclaration,
        suggestedNames: List<String>,
        physicalExpression: KtExpression,
        replacementType: KaType,
        parametersUsages: MultiMap<KtElement, KtElement>,
        occurrencesToReplace: List<KotlinPsiRange>,
        psiFactory: KtPsiFactory,
        argumentValue: KtExpression?
    ): IntroduceParameterDescriptor<KtNamedDeclaration> = helper.configure(
        IntroduceParameterDescriptor(
            originalRange = originalExpression.toRange(),
            callable = targetParent,
            callableDescriptor = targetParent,
            newParameterName = suggestedNames.first().quoteIfNeeded(),
            newParameterTypeText = analyze(physicalExpression) {
                replacementType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
                    functionalTypeRenderer = KaFunctionalTypeRenderer.AS_FUNCTIONAL_TYPE
                }, position = Variance.IN_VARIANCE)
            },
            argumentValue = argumentValue,
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
                            //it's under potemkin progress
                            allowAnalysisOnEdt { allowAnalysisFromWriteAction { analyze(lambdaArgument) { NamedArgumentUtils.getStableNameFor(lambdaArgument) } } }
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

@OptIn(KaExperimentalApi::class)
fun IntroduceParameterDescriptor<KtNamedDeclaration>.performRefactoring(editor: Editor) {
    val superMethods = checkSuperMethods(callable, emptyList(), RefactoringBundle.message("to.refactor"))
    val targetCallable = superMethods.firstOrNull() ?: return

    if (!targetCallable.canRefactorElement()) {
        val unmodifiableFileName = targetCallable.containingFile?.name
        val message = RefactoringBundle.message("refactoring.cannot.be.performed") + "\n" +
                KotlinBundle.message(
                    "error.hint.cannot.modify.0.declaration.from.1.file",
                    DescriptiveNameUtil.getDescriptiveName(targetCallable),
                    unmodifiableFileName!!,
                )
        CommonRefactoringUtil.showErrorHint(callable.project, editor, message, CommonBundle.getErrorTitle(), INTRODUCE_PARAMETER)
        return
    }

    if (targetCallable is PsiMethod) {
        val typeReference =
            KtPsiFactory.contextual(callable).createType(newParameterTypeText, callable, callable, Variance.INVARIANT)

        val psiType = analyzeInModalWindow(typeReference, KotlinBundle.message("fix.change.signature.prepare")) {
            typeReference.type.asPsiType(targetCallable, true)
        }

        val newParam = ParameterInfoImpl.create(-1).withName(newParameterName).withType(psiType)
        val newParameters = ParameterInfoImpl.fromMethod(targetCallable) + newParam
        object : ChangeSignatureProcessor(
            targetCallable.project,
            targetCallable,
            false,
            null,
            targetCallable.name,
            targetCallable.returnType,
            newParameters
        ) {
            override fun performRefactoring(usages: Array<out UsageInfo?>) {
                super.performRefactoring(usages)
                occurrencesToReplace.forEach {
                    occurrenceReplacer(it)
                }
            }
        }.run()
    }

    if (targetCallable !is KtNamedDeclaration) return

    val methodDescriptor = KotlinMethodDescriptor((targetCallable as? KtClass)?.primaryConstructor ?: targetCallable)
    val changeInfo = KotlinChangeInfo(methodDescriptor)

    val defaultValue = (if (newArgumentValue is KtProperty) (newArgumentValue as KtProperty).initializer else newArgumentValue)?.let { KtPsiUtil.safeDeparenthesize(it) }

    if (!withDefaultValue) {
        val parameters = callable.getValueParameters()
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

    val containingParameter = originalRange.elements.firstOrNull()?.parentOfType<KtParameter>()

    val targetParameterIndex = containingParameter?.let { (callable as? KtFunction)?.valueParameterList?.parameters?.indexOf(containingParameter) } ?: -1

    changeInfo.addParameter(parameterInfo, targetParameterIndex)

    object : KotlinChangeSignatureProcessor(targetCallable.project, changeInfo) {
        override fun performRefactoring(usages: Array<out UsageInfo?>) {
            super.performRefactoring(usages)
            occurrencesToReplace.forEach {
                occurrenceReplacer(it)
            }
        }
    }.run()
}

open class KotlinFirIntroduceLambdaParameterHandler(
    private val helper: KotlinIntroduceParameterHelper<KtNamedDeclaration> = KotlinIntroduceParameterHelper.Default()
) : KotlinFirIntroduceParameterHandler(helper) {
    @OptIn(KaExperimentalApi::class)
    private val extractLambdaHelper = object : ExtractionEngineHelper(INTRODUCE_LAMBDA_PARAMETER) {
        private fun createDialog(
            project: Project,
            editor: Editor,
            lambdaExtractionDescriptor: ExtractableCodeDescriptor
        ): KotlinIntroduceParameterDialog {
            val callable = lambdaExtractionDescriptor.extractionData.targetSibling as KtNamedDeclaration
            val originalRange = lambdaExtractionDescriptor.extractionData.originalRange
            val (parametersUsages, returnType) = analyzeInModalWindow(callable, KotlinBundle.message("fix.change.signature.prepare")) {
                findInternalUsagesOfParametersAndReceiver(callable) to calculateFunctionalType(lambdaExtractionDescriptor)
            }
            val introduceParameterDescriptor = IntroduceParameterDescriptor(
                originalRange = originalRange,
                callable = callable,
                callableDescriptor = callable,
                newParameterName = "", // to be chosen in the dialog
                newParameterTypeText = "", // to be chosen in the dialog
                argumentValue = KtPsiFactory(project).createExpression("{}"), // substituted later
                withDefaultValue = false,
                parametersUsages = parametersUsages,
                occurrencesToReplace = listOf(originalRange),
                parametersToRemove = listOf()
            )
            return KotlinIntroduceParameterDialog(project, editor, introduceParameterDescriptor,
                                                  lambdaExtractionDescriptor.suggestedNames.toTypedArray(),
                                                  listOf(returnType),
                                                  helper,
                                                  lambdaExtractionDescriptor)
        }

        context(KaSession)
        fun calculateFunctionalType(
            oldDescriptor: ExtractableCodeDescriptor,
        ): String {
            val receiverText = oldDescriptor.receiverParameter?.parameterType?.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.IN_VARIANCE)?.let { "$it." } ?: ""
            val parameters =
                oldDescriptor.parameters.joinToString(", ", "(", ")") { it.parameterType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.IN_VARIANCE) }

            val returnTypeText = oldDescriptor.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE)
            return "$receiverText$parameters -> $returnTypeText"
        }

        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            val lambdaExtractionDescriptor = descriptorWithConflicts.descriptor
            if (!ExtractionTarget.FAKE_LAMBDALIKE_FUNCTION.isAvailable(lambdaExtractionDescriptor)) {
                showErrorHint(
                    project,
                    editor,
                    KotlinBundle.message("error.text.can.t.introduce.lambda.parameter.for.this.expression"),
                    INTRODUCE_LAMBDA_PARAMETER
                )
                return
            }

            val dialog = createDialog(project, editor, lambdaExtractionDescriptor) ?: return
            if (isUnitTestMode()) {
                dialog.performRefactoring()
            } else {
                dialog.showAndGet()
            }
        }
    }

    override fun invoke(project: Project, editor: Editor, expression: KtExpression, targetParent: KtNamedDeclaration) {
        val duplicateContainer =
            when (targetParent) {
                is KtFunction -> targetParent.bodyExpression
                is KtClass -> targetParent.body
                else -> null
            } ?: throw AssertionError("Body element is not found: ${targetParent.getElementTextWithContext()}")
        val extractionData = ActionUtil.underModalProgress(project, KotlinBundle.message("fix.change.signature.prepare")) {
            ExtractionData(
                targetParent.containingKtFile,
                expression.toRange(),
                targetParent,
                duplicateContainer,
                ExtractionOptions.DEFAULT
            )
        }
        val engine = object :
            IExtractionEngine<KaType, ExtractionData, ExtractionGeneratorConfiguration, ExtractionResult, ExtractableCodeDescriptor, ExtractableCodeDescriptorWithConflicts>(
                extractLambdaHelper
            ) {
            override fun performAnalysis(extractionData: ExtractionData): AnalysisResult<KaType> {
                return ExtractionDataAnalyzer(extractionData).performAnalysis()
            }
        }
        engine.run(editor, extractionData)
    }
}