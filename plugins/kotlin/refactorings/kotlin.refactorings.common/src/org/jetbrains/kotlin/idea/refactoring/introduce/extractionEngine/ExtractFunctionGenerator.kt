// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.refactoring.addElement
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValueBoxer.AsTuple
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiFactory.CallableBuilder
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.util.*

private var KtExpression.isJumpElementToReplace: Boolean
        by NotNullablePsiCopyableUserDataProperty(Key.create("IS_JUMP_ELEMENT_TO_REPLACE"), false)

private var KtReturnExpression.isReturnForLabelRemoval: Boolean
        by NotNullablePsiCopyableUserDataProperty(Key.create("IS_RETURN_FOR_LABEL_REMOVAL"), false)

abstract class ExtractFunctionGenerator<KotlinType, ExtractionResult : IExtractionResult<KotlinType>> {
    abstract val nameGenerator: IExtractionNameSuggester<KotlinType>
    abstract fun createTypeDescriptor(data: IExtractionData): TypeDescriptor<KotlinType>

    abstract fun IExtractionGeneratorConfiguration<KotlinType>.collapseBody(blockExpression: KtBlockExpression)

    abstract fun resolveNameConflict(property: KtProperty)

    abstract fun checkTypeArgumentsAreRedundant(args: KtTypeArgumentList): Boolean

    abstract fun IExtractionGeneratorConfiguration<KotlinType>.createExtractionResult(
        declaration: KtNamedDeclaration,
        duplicatesReplacer: Map<KotlinPsiRange, () -> Unit>
    ): ExtractionResult

    fun getSignaturePreview(config: IExtractionGeneratorConfiguration<KotlinType>) =
        buildSignature(config.generatorOptions, config.descriptor, false).asString()

    fun generateDeclaration(
        config: IExtractionGeneratorConfiguration<KotlinType>,
        declarationToReplace: KtNamedDeclaration? = null,
    ): ExtractionResult {
        val descriptor = config.descriptor
        val generatorOptions = config.generatorOptions
        val psiFactory = KtPsiFactory(descriptor.extractionData.project)
        val defaultValue = descriptor.controlFlow.defaultOutputValue
        val byReturnType = if (descriptor.isUnitReturnType()) null else
            nameGenerator.suggestNamesByType(descriptor.returnType,
                                             descriptor.extractionData.commonParent,
                                             validator = { true }).firstOrNull()

        val resultNamesByValue = defaultValue?.valueType?.let {
            nameGenerator.suggestNamesByType(
                it,
                descriptor.extractionData.commonParent,
                validator = { true })
        }

        fun getReturnsForLabelRemoval() = descriptor.controlFlow.outputValues
            .flatMapTo(arrayListOf()) { it.originalExpressions.filterIsInstance<KtReturnExpression>() }

        fun createDeclaration(): KtNamedDeclaration {
            descriptor.controlFlow.jumpOutputValue?.elementsToReplace?.forEach { it.isJumpElementToReplace = true }
            getReturnsForLabelRemoval().forEach { it.isReturnForLabelRemoval = true }

            return with(descriptor.extractionData) {
                if (generatorOptions.inTempFile) {
                    createTemporaryDeclaration("${getDeclarationPattern(generatorOptions, descriptor)}\n")
                } else {
                    psiFactory.createDeclarationByPattern(
                        getDeclarationPattern(generatorOptions, descriptor),
                        PsiChildRange(originalElements.firstOrNull(), originalElements.lastOrNull())
                    )
                }
            }
        }

        fun getReturnArguments(resultExpression: KtExpression?): List<KtExpression> {
            return descriptor.controlFlow.outputValues
                .mapNotNull {
                    when (it) {
                        is ExpressionValue -> resultExpression
                        is Jump -> if (it.conditional) psiFactory.createExpression("false") else null
                        is ParameterUpdate -> psiFactory.createExpression(it.parameter.nameForRef)
                        is Initializer -> psiFactory.createExpression(it.initializedDeclaration.name!!.quoteIfNeeded())
                        else -> throw IllegalArgumentException("Unknown output value: $it")
                    }
                }
        }

        fun KtExpression.replaceWithReturn(replacingExpression: KtReturnExpression) {
            descriptor.controlFlow.defaultOutputValue?.let {
                val boxedExpression = replaced(replacingExpression).returnedExpression!!
                descriptor.controlFlow.outputValueBoxer.extractExpressionByValue(boxedExpression, it)
            }
        }

        fun adjustDeclarationBody(declaration: KtNamedDeclaration) {
            val body = declaration.getGeneratedBody()

            (body.blockExpressionsOrSingle().singleOrNull() as? KtExpression)?.let {
                if (it.mustBeParenthesizedInInitializerPosition()) {
                    it.replace(psiFactory.createExpressionByPattern("($0)", it))
                }
            }

            val jumpValue = descriptor.controlFlow.jumpOutputValue
            if (jumpValue != null) {
                val replacingReturn = psiFactory.createExpression(if (jumpValue.conditional) "return true" else "return")
                body.collectDescendantsOfType<KtExpression> { it.isJumpElementToReplace }.forEach {
                    it.replace(replacingReturn)
                    it.isJumpElementToReplace = false
                }
            }

            body.collectDescendantsOfType<KtReturnExpression> { it.isReturnForLabelRemoval }.forEach {
                it.getTargetLabel()?.delete()
                it.isReturnForLabelRemoval = false
            }

            /*
             * Sort by descending position so that internals of value/type arguments in calls and qualified types are replaced
             * before calls/types themselves
             */
            val currentRefs = body
                .collectDescendantsOfType<KtReferenceExpression> { it.resolveResult != null }
                .sortedByDescending { it.startOffset }

            currentRefs.forEach {
                val resolveResult = it.resolveResult!!
                val currentRef = if (it.isValid) {
                    it
                } else {
                    body.findDescendantOfType { expr -> expr.resolveResult == resolveResult } ?: return@forEach
                }
                val originalRef = resolveResult.originalRefExpr as? KtSimpleNameExpression ?: return@forEach
                val newRef = descriptor.replacementMap[originalRef]
                    .fold(currentRef as KtElement) { ref, replacement -> replacement(descriptor, ref) }
                (newRef as? KtSimpleNameExpression)?.resolveResult = resolveResult
            }

            if (generatorOptions.target == ExtractionTarget.PROPERTY_WITH_INITIALIZER) return

            if (body !is KtBlockExpression) throw AssertionError("Block body expected: ${descriptor.extractionData.codeFragmentText}")

            val firstExpression = body.statements.firstOrNull()
            if (firstExpression != null) {
                for (param in descriptor.parameters) {
                    param.mirrorVarName?.let { varName ->
                        body.addBefore(psiFactory.createProperty(varName, null, true, param.name), firstExpression)
                        body.addBefore(psiFactory.createNewLine(), firstExpression)
                    }
                }
            }

            val lastExpression = body.statements.lastOrNull()
            if (lastExpression is KtReturnExpression) return

            val defaultExpression =
                if (!generatorOptions.inTempFile && defaultValue != null && descriptor.controlFlow.outputValueBoxer
                        .boxingRequired && lastExpression!!.isMultiLine()
                ) {
                    require(resultNamesByValue != null) { "defaultValue is checked to be not-null" }
                    val resultVal = resultNamesByValue.map { nameGenerator.suggestNameByName(it, body, lastExpression) }.first()
                    body.addBefore(psiFactory.createDeclaration("val $resultVal = ${lastExpression.text}"), lastExpression)
                    body.addBefore(psiFactory.createNewLine(), lastExpression)
                    psiFactory.createExpression(resultVal)
                } else lastExpression

            val returnExpression =
                descriptor.controlFlow.outputValueBoxer.getReturnExpression(getReturnArguments(defaultExpression), psiFactory) ?: return

            when (generatorOptions.target) {
                ExtractionTarget.LAZY_PROPERTY, ExtractionTarget.FAKE_LAMBDALIKE_FUNCTION -> {
                    // In the case of lazy property absence of default value means that output values are of OutputValue.Initializer type
                    // We just add resulting expressions without return, since returns are prohibited in the body of lazy property
                    if (defaultValue == null) {
                        body.addElement(returnExpression.returnedExpression!!)
                    }
                    return
                }

                else -> {}
            }

            when {
                defaultValue == null -> body.addElement(returnExpression)
                !defaultValue.callSiteReturn || defaultValue.hasImplicitReturn -> lastExpression!!.replaceWithReturn(returnExpression)
            }

            if (generatorOptions.allowExpressionBody) {
                config.collapseBody(body)
            }
        }

        @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
        fun makeCall(
            extractableDescriptor: IExtractableCodeDescriptor<KotlinType>,
            declaration: KtNamedDeclaration,
            controlFlow: ControlFlow<KotlinType>,
            rangeToReplace: KotlinPsiRange,
            arguments: List<String>
        ) {
            fun insertCall(anchor: PsiElement, wrappedCall: KtExpression): KtExpression? {
                val firstExpression = rangeToReplace.elements.firstIsInstanceOrNull<KtExpression>()
                if (firstExpression?.isLambdaOutsideParentheses() == true) {
                    val functionLiteralArgument = firstExpression.getStrictParentOfType<KtLambdaArgument>()!!
                    val lambdaArgument = firstExpression.getContainingLambdaOutsideParentheses()
                        ?.takeIf { shouldLambdaParameterBeNamed(it) }

                    val lambdaName = if (lambdaArgument != null) {
                        allowAnalysisOnEdt {
                            allowAnalysisFromWriteAction {
                                analyze(lambdaArgument) {
                                    NamedArgumentUtils.getStableNameFor(lambdaArgument)
                                }
                            }
                        }
                    } else null
                    return functionLiteralArgument.moveInsideParenthesesAndReplaceWith(wrappedCall, lambdaName)
                }

                if (anchor is KtOperationReferenceExpression) {
                    val newNameExpression = when (val operationExpression = anchor.parent as? KtOperationExpression ?: return null) {
                        is KtUnaryExpression -> OperatorToFunctionConverter.convert(operationExpression).second
                        is KtBinaryExpression -> {
                            convertInfixCallToOrdinary(operationExpression).getCalleeExpressionIfAny()
                        }

                        else -> null
                    }
                    return newNameExpression?.replaced(wrappedCall)
                }

                (anchor as? KtExpression)?.extractableSubstringInfo?.let {
                    return it.replaceWith(wrappedCall)
                }

                return anchor.replaced(wrappedCall)
            }

            if (rangeToReplace.isEmpty) return

            val anchor = rangeToReplace.elements.first()
            val anchorParent = anchor.parent!!

            anchor.nextSibling?.let { from ->
                val to = rangeToReplace.elements.last()
                if (to != anchor) {
                    anchorParent.deleteChildRange(from, to)
                }
            }

            val calleeName = declaration.name?.quoteIfNeeded()
            val callText = when (declaration) {
                is KtNamedFunction -> {
                    val argumentsText = arguments.joinToString(separator = ", ", prefix = "(", postfix = ")")
                    val typeArguments = extractableDescriptor.typeParameters.map { it.originalDeclaration.name }
                    val typeArgumentsText = with(typeArguments) {
                        if (isNotEmpty()) joinToString(separator = ", ", prefix = "<", postfix = ">") else ""
                    }
                    "$calleeName$typeArgumentsText$argumentsText"
                }

                else -> calleeName
            }

            val anchorInBlock = generateSequence(anchor) { it.parent }.firstOrNull { it.parent is KtBlockExpression }
            val block = (anchorInBlock?.parent as? KtBlockExpression) ?: anchorParent as KtElement

            val psiFactory = KtPsiFactory(anchor.project)
            val newLine = psiFactory.createNewLine()

            if (controlFlow.outputValueBoxer is AsTuple && controlFlow.outputValues.size > 1 && controlFlow.outputValues
                    .all { it is Initializer }
            ) {
                val declarationsToMerge = controlFlow.outputValues.map { (it as Initializer).initializedDeclaration }
                val isVar = declarationsToMerge.first().isVar
                if (declarationsToMerge.all { it.isVar == isVar }) {
                    controlFlow.declarationsToCopy.subtract(declarationsToMerge).forEach {
                        block.addBefore(psiFactory.createDeclaration(it.text!!), anchorInBlock) as KtDeclaration
                        block.addBefore(newLine, anchorInBlock)
                    }

                    val entries = declarationsToMerge.map { p -> p.name + (p.typeReference?.let { ": ${it.text}" } ?: "") }
                    anchorInBlock?.replace(
                        psiFactory.createDestructuringDeclaration("${if (isVar) "var" else "val"} (${entries.joinToString()}) = $callText")
                    )

                    return
                }
            }

            val inlinableCall = controlFlow.outputValues.size <= 1
            val unboxingExpressions =
                if (inlinableCall) {
                    controlFlow.outputValueBoxer.getUnboxingExpressions(callText ?: return)
                } else {
                    val resultVal = nameGenerator.suggestNameByName(byReturnType ?: "result", block, anchorInBlock)

                    block.addBefore(psiFactory.createDeclaration("val $resultVal = $callText"), anchorInBlock)
                    block.addBefore(newLine, anchorInBlock)
                    controlFlow.outputValueBoxer.getUnboxingExpressions(resultVal)
                }

            val copiedDeclarations = HashMap<KtDeclaration, KtDeclaration>()
            for (decl in controlFlow.declarationsToCopy) {
                val declCopy = psiFactory.createDeclaration<KtDeclaration>(decl.text!!)
                copiedDeclarations[decl] = block.addBefore(declCopy, anchorInBlock) as KtDeclaration
                block.addBefore(newLine, anchorInBlock)
            }

            if (controlFlow.outputValues.isEmpty()) {
                anchor.replace(psiFactory.createExpression(callText!!))
                return
            }

            fun wrapCall(outputValue: OutputValue<KotlinType>, callText: String): List<PsiElement> {
                return when (outputValue) {
                    is ExpressionValue -> {
                        val exprText = if (outputValue.callSiteReturn) {
                            val firstReturn =
                                outputValue.originalExpressions.asSequence().filterIsInstance<KtReturnExpression>().firstOrNull()
                            val label = firstReturn?.getTargetLabel()?.text ?: ""
                            "return$label $callText"
                        } else {
                            callText
                        }
                        Collections.singletonList(psiFactory.createExpression(exprText))
                    }

                    is ParameterUpdate ->
                        Collections.singletonList(
                            psiFactory.createExpression("${outputValue.parameter.argumentText} = $callText")
                        )

                    is Jump -> {
                        val elementToInsertAfterCall = outputValue.elementToInsertAfterCall
                        when {
                            elementToInsertAfterCall == null -> listOf(
                                newLine,
                                psiFactory.createExpression(callText)
                            )
                            outputValue.conditional -> Collections.singletonList(
                                psiFactory.createExpression("if ($callText) ${elementToInsertAfterCall.text}")
                            )

                            else -> listOf(
                                psiFactory.createExpression(callText),
                                newLine,
                                psiFactory.createExpression(elementToInsertAfterCall.text!!)
                            )
                        }
                    }

                    is Initializer -> {
                        val newProperty = copiedDeclarations[outputValue.initializedDeclaration] as KtProperty
                        newProperty.initializer = psiFactory.createExpression(callText)
                        Collections.emptyList()
                    }

                    else -> throw IllegalArgumentException("Unknown output value: $outputValue")
                }
            }

            val defaultValue = controlFlow.defaultOutputValue

            val elements = controlFlow.outputValues
                .filter { it != defaultValue }
                .flatMap { wrapCall(it, unboxingExpressions.getValue(it)) }

            val needParenthesis = anchorParent !is KtBlockExpression && anchorParent !is KtClassBody && anchorParent !is KtFile
            if (controlFlow.outputValues.size < 2 && elements.size > 1 && needParenthesis) {
                val wrapperBlock = psiFactory.createExpression(
                    """{
                       ${elements.joinToString(separator = "") { it.text }}
                    }""".trimIndent()
                )
                anchor.replace(wrapperBlock)
            } else {
                elements
                    .withIndex()
                    .forEach { (i, wrappedCall) ->
                        if (i > 0) {
                            block.addBefore(newLine, anchorInBlock)
                        }
                        block.addBefore(wrappedCall, anchorInBlock)
                    }
            }

            defaultValue?.let {
                if (!inlinableCall) {
                    block.addBefore(newLine, anchorInBlock)
                }
                insertCall(
                    anchor,
                    wrapCall(it, unboxingExpressions.getValue(it)).first() as KtExpression
                )?.removeTemplateEntryBracesIfPossible()
            }

            if (anchor.isValid) {
                anchor.delete()
            }
        }

        val duplicates = if (generatorOptions.inTempFile) Collections.emptyList() else descriptor.duplicates

        val anchor = with(descriptor.extractionData) {
            val targetParent = targetSibling.parent

            val anchorCandidates = duplicates.mapTo(arrayListOf()) { it.range.elements.first().substringContextOrThis }
            anchorCandidates.add(targetSibling)
            if (targetSibling is KtEnumEntry) {
                anchorCandidates.add(targetSibling.siblings().last { it is KtEnumEntry })
            }

            val marginalCandidate = if (insertBefore) {
                anchorCandidates.minByOrNull { it.startOffset }!!
            } else {
                anchorCandidates.maxByOrNull { it.startOffset }!!
            }

            // Ascend to the level of targetSibling
            marginalCandidate.parentsWithSelf.first { it.parent == targetParent }
        }

        val shouldInsert = !(generatorOptions.inTempFile || generatorOptions.target == ExtractionTarget.FAKE_LAMBDALIKE_FUNCTION)
        val declaration =
            createDeclaration().let { if (shouldInsert) insertDeclaration(declarationToReplace, descriptor, it, anchor) else it }
        adjustDeclarationBody(declaration)

        if (generatorOptions.inTempFile) return config.createExtractionResult(declaration, Collections.emptyMap())

        val replaceInitialOccurrence = {
            val arguments = descriptor.parameters.filter { !it.contextParameter }.map { it.argumentText }
            makeCall(descriptor, declaration, descriptor.controlFlow, descriptor.extractionData.originalRange, arguments)
        }

        if (!generatorOptions.delayInitialOccurrenceReplacement) replaceInitialOccurrence()

        if (shouldInsert) {
            ShortenReferencesFacility.getInstance().shorten(declaration)
        }

        val duplicateReplacers = LinkedHashMap<KotlinPsiRange, () -> Unit>().apply {
            if (generatorOptions.delayInitialOccurrenceReplacement) {
                put(descriptor.extractionData.originalRange, replaceInitialOccurrence)
            }
            putAll(duplicates.map {
                val smartListRange = KotlinPsiRange.SmartListRange(it.range.elements)
                smartListRange to { makeCall(descriptor, declaration, it.controlFlow, smartListRange, it.arguments) }
            })
        }

        if (descriptor.typeParameters.isNotEmpty()) {
            for (ref in ReferencesSearch.search(declaration, LocalSearchScope(descriptor.getOccurrenceContainer()!!)).asIterable()) {
                val typeArgumentList = (ref.element.parent as? KtCallExpression)?.typeArgumentList ?: continue
                if (checkTypeArgumentsAreRedundant(typeArgumentList)) {
                    typeArgumentList.delete()
                }
            }
        }

        if (declaration is KtProperty) {
            if (declaration.isExtensionDeclaration() && !declaration.isTopLevel) {
                val receiverTypeReference = (declaration as? KtCallableDeclaration)?.receiverTypeReference
                receiverTypeReference?.siblings(withItself = false)?.firstOrNull { it.node.elementType == KtTokens.DOT }?.delete()
                receiverTypeReference?.delete()
            }
            resolveNameConflict(declaration)
        }

        CodeStyleManager.getInstance(descriptor.extractionData.project).reformat(declaration)

        return config.createExtractionResult(declaration, duplicateReplacers)
    }

    private fun getDeclarationPattern(
        options: ExtractionGeneratorOptions,
        descriptor: IExtractableCodeDescriptor<KotlinType>,
    ): String {
        val extractionTarget = options.target
        if (!extractionTarget.isAvailable(descriptor)) {
            throw BaseRefactoringProcessor.ConflictsInTestsException(
                listOf(
                    KotlinBundle.message(
                        "error.text.can.t.generate.0.1",
                        extractionTarget.targetName,
                        descriptor.extractionData.codeFragmentText
                    )
                )
            )
        }

        return buildSignature(options, descriptor, true).let { builder ->
            builder.transform {
                for (i in generateSequence(indexOf('$')) { indexOf('$', it + 2) }) {
                    if (i < 0) break
                    insert(i + 1, '$')
                }
            }

            when (extractionTarget) {
                ExtractionTarget.FUNCTION,
                ExtractionTarget.FAKE_LAMBDALIKE_FUNCTION,
                ExtractionTarget.PROPERTY_WITH_GETTER -> builder.blockBody("$0")

                ExtractionTarget.PROPERTY_WITH_INITIALIZER -> builder.initializer("$0")
                ExtractionTarget.LAZY_PROPERTY -> builder.lazyBody("$0")
            }

            builder.asString()
        }
    }

    private fun buildSignature(
        options: ExtractionGeneratorOptions,
        descriptor: IExtractableCodeDescriptor<KotlinType>,
        renderAnnotations: Boolean
    ): CallableBuilder {
        val extractionTarget = options.target
        if (!extractionTarget.isAvailable(descriptor)) {
            val message = KotlinBundle.message(
                "error.text.can.t.generate.0.1",
                extractionTarget.targetName,
                descriptor.extractionData.codeFragmentText
            )
            throw BaseRefactoringProcessor.ConflictsInTestsException(listOf(message))
        }

        val builderTarget = when (extractionTarget) {
            ExtractionTarget.FUNCTION, ExtractionTarget.FAKE_LAMBDALIKE_FUNCTION -> CallableBuilder.Target.FUNCTION
            else -> CallableBuilder.Target.READ_ONLY_PROPERTY
        }
        return CallableBuilder(builderTarget).apply {

            val typeDescriptor = createTypeDescriptor(descriptor.extractionData)

            val contextParameters = descriptor.parameters.filter { it.contextParameter }
            if (contextParameters.isNotEmpty()) {
                val contextString = contextParameters.joinToString(prefix = "context(", postfix = ")") {
                    it.name + ": " + typeDescriptor.renderType(it.parameterType, isReceiver = false, Variance.IN_VARIANCE)
                }
                modifier(contextString)
            }

            val visibility = descriptor.visibility?.value ?: ""

            fun TypeParameter.isReified() = originalDeclaration.hasModifier(KtTokens.REIFIED_KEYWORD)
            val shouldBeInline = descriptor.typeParameters.any { it.isReified() }

            val optInAnnotation = if (extractionTarget != ExtractionTarget.FUNCTION || descriptor.optInMarkers.isEmpty()) {
                ""
            } else {
                val innerText = descriptor.optInMarkers.joinToString(separator = ", ") { "${it.shortName().render()}::class" }
                "@${OptInNames.OPT_IN_FQ_NAME.shortName().render()}($innerText)\n"
            }

            val annotations = if (descriptor.annotationsText.isEmpty() || !renderAnnotations) {
                ""
            } else {
                descriptor.annotationsText
            }
            val extraModifiers = descriptor.modifiers.map { it.value } +
                    listOfNotNull(if (shouldBeInline) KtTokens.INLINE_KEYWORD.value else null) +
                    listOfNotNull(if (options.isConst) KtTokens.CONST_KEYWORD.value else null)
            val modifiers = if (visibility.isNotEmpty()) listOf(visibility) + extraModifiers else extraModifiers
            modifier(annotations + optInAnnotation + modifiers.joinToString(separator = " "))

            typeParams(
                descriptor.typeParameters.map {
                    val typeParameter = it.originalDeclaration
                    val bound = typeParameter.extendsBound

                    buildString {
                        if (it.isReified()) {
                            append(KtTokens.REIFIED_KEYWORD.value)
                            append(' ')
                        }
                        append(typeParameter.name)
                        if (bound != null) {
                            append(" : ")
                            append(bound.text)
                        }
                    }
                }
            )

            descriptor.receiverParameter?.let {
                val receiverType = it.parameterType
                val receiverTypeAsString = typeDescriptor.renderType(receiverType, isReceiver = true, Variance.IN_VARIANCE)
                receiver(receiverTypeAsString)
            }

            name(descriptor.name)

            descriptor.parameters.filter { !it.contextParameter }.forEach { parameter ->
                param(parameter.name, typeDescriptor.renderType(parameter.parameterType, isReceiver = false, Variance.IN_VARIANCE))
            }

            val returnType = descriptor.returnType
            val presentation = typeDescriptor.renderType(returnType, isReceiver = false, Variance.OUT_VARIANCE)
            if (typeDescriptor.unitType == returnType ||
                with(typeDescriptor) { returnType.isError() } ||
                extractionTarget == ExtractionTarget.PROPERTY_WITH_INITIALIZER && returnType !is KaFunctionType) {
                noReturnType()
            } else {
                returnType(presentation)
            }

            typeConstraints(descriptor.typeParameters.flatMap { it.originalConstraints }.map { it.text!! })
        }
    }

    private fun insertDeclaration(
        declarationToReplace: KtNamedDeclaration? = null,
        descriptor: IExtractableCodeDescriptor<*>,
        declaration: KtNamedDeclaration, anchor: PsiElement
    ): KtNamedDeclaration {
        declarationToReplace?.let { return it.replace(declaration) as KtNamedDeclaration }

        val psiFactory = KtPsiFactory(descriptor.extractionData.project)
        return with(descriptor.extractionData) {
            val targetContainer = anchor.parent!!
            // TODO: Get rid of explicit new-lines in favor of formatter rules
            val emptyLines = psiFactory.createWhiteSpace("\n\n")
            if (insertBefore) {
                (targetContainer.addBefore(declaration, anchor) as KtNamedDeclaration).apply {
                    targetContainer.addBefore(emptyLines, anchor)
                }
            } else {
                (targetContainer.addAfter(declaration, anchor) as KtNamedDeclaration).apply {
                    if (!(targetContainer is KtClassBody && (targetContainer.parent as? KtClass)?.isEnum() == true)) {
                        targetContainer.addAfter(emptyLines, anchor)
                    }
                    val insertedDeclaration = this
                    PostInsertDeclarationCallback.EP_NAME.forEachExtensionSafe { extension ->
                        extension.declarationInserted(insertedDeclaration, targetContainer, psiFactory)
                    }
                }
            }
        }
    }

}