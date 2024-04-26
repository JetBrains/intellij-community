// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult.ErrorMessage
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult.Status
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.ExpressionValue
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.Jump
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

abstract class AbstractExtractionDataAnalyzer<KotlinType, P : IMutableParameter<KotlinType>>(private val data: IExtractionData) {
    abstract val typeDescriptor: TypeDescriptor<KotlinType>

    abstract val nameSuggester: IExtractionNameSuggester<KotlinType>

    abstract fun createOutputDescriptor(): OutputDescriptor<KotlinType>
    abstract fun getLocalDeclarationsWithNonLocalUsages(): List<KtNamedDeclaration>
    abstract fun getModifiedVars(): Map<String, List<KtExpression>>
    abstract fun getVarDescriptorsAccessedAfterwards(): Set<String>
    abstract fun inferParametersInfo(
        virtualBlock: KtBlockExpression,
        modifiedVariables: Set<String>
    ): ParametersInfo<KotlinType, P>

    abstract fun hasSyntaxErrors(): Boolean

    abstract fun createDescriptor(
        suggestFunctionNames: List<String>,
        defaultVisibility: KtModifierKeywordToken?,
        parameters: List<P>,
        receiverParameter: P?,
        typeParameters: List<TypeParameter>,
        replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KotlinType>>,
        flow: ControlFlow<KotlinType>,
        returnType: KotlinType
    ): IExtractableCodeDescriptor<KotlinType>

    fun performAnalysis(): AnalysisResult<KotlinType> {
        if (data.originalElements.isEmpty()) return AnalysisResult<KotlinType>(
            null,
            Status.CRITICAL_ERROR,
            listOf(ErrorMessage.NO_EXPRESSION)
        )

        if (getParentOfType(data.commonParent, KtDeclarationWithBody::class.java, KtClassOrObject::class.java, KtScript::class.java) == null
            && data.commonParent.getNonStrictParentOfType<KtProperty>() == null
        ) {
            return AnalysisResult<KotlinType>(null, Status.CRITICAL_ERROR, listOf(ErrorMessage.NO_CONTAINER))
        }

        if (hasSyntaxErrors()) return AnalysisResult(null, Status.CRITICAL_ERROR, listOf(ErrorMessage.SYNTAX_ERRORS))

        val modifiedVarDescriptorsWithExpressions = getModifiedVars()

        val virtualBlock = data.createTemporaryCodeBlock()
        val paramsInfo = inferParametersInfo(
            virtualBlock,
            modifiedVarDescriptorsWithExpressions.keys
        )

        if (paramsInfo.errorMessage != null) {
            return AnalysisResult(null, Status.CRITICAL_ERROR, listOf(paramsInfo.errorMessage!!))
        }

        val nonLocallyUsedDeclarations = getLocalDeclarationsWithNonLocalUsages()
        val (localVariablesToCopy, declarationsToReport) = nonLocallyUsedDeclarations.partition { it is KtProperty && it.isLocal }
        if (declarationsToReport.isNotEmpty()) {
            val localVarStr = declarationsToReport.map { typeDescriptor.renderForMessage(it)!! }.distinct().sorted()
            return AnalysisResult(
                null,
                Status.CRITICAL_ERROR,
                listOf(ErrorMessage.DECLARATIONS_ARE_USED_OUTSIDE.addAdditionalInfo(localVarStr))
            )
        }

        val messages = ArrayList<ErrorMessage>()

        val modifiedVarDescriptorsForControlFlow = HashMap(modifiedVarDescriptorsWithExpressions)
        modifiedVarDescriptorsForControlFlow.keys.retainAll(getVarDescriptorsAccessedAfterwards())

        val outputDescriptor = createOutputDescriptor()

        val (controlFlow, controlFlowMessage) =
            ControlFlowBuilder.analyzeControlFlow(
                data,
                outputDescriptor,
                modifiedVarDescriptorsForControlFlow,
                paramsInfo.parameters,
                localVariablesToCopy.mapNotNull { it as? KtProperty },
                typeDescriptor
            )
        controlFlowMessage?.let { messages.add(it) }
        val returnType = controlFlow.outputValueBoxer.returnType
        returnType.processTypeIfExtractable(
            paramsInfo.typeParameters,
            paramsInfo.nonDenotableTypes,
            true,
            typeDescriptor::typeArguments,
            typeDescriptor::isResolvableInScope
        )

        if (paramsInfo.nonDenotableTypes.isNotEmpty()) {
            val typeStr = paramsInfo.nonDenotableTypes.map { typeDescriptor.renderTypeWithoutApproximation(it) }.sorted()
            return AnalysisResult(
                null,
                Status.CRITICAL_ERROR,
                listOf(ErrorMessage.DENOTABLE_TYPES.addAdditionalInfo(typeStr))
            )
        }

        data.commonParent.getStrictParentOfType<KtDeclaration>()?.let { enclosingDeclaration ->
            data.checkDeclarationsMovingOutOfScope(enclosingDeclaration, controlFlow) { typeDescriptor.renderForMessage(it)!! }
                ?.let { messages.add(it) }
        }

        controlFlow.jumpOutputValue?.elementToInsertAfterCall?.accept(
            object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    paramsInfo.originalRefToParameter[expression].firstOrNull()?.let { it.refCount-- }
                }
            }
        )

        val adjustedParameters = paramsInfo.parameters.filterTo(LinkedHashSet<P>()) { it.refCount > 0 }

        val receiverCandidates = adjustedParameters.filterTo(hashSetOf()) { it.receiverCandidate }
        val receiverParameter = if (receiverCandidates.size == 1 && !data.options.canWrapInWith) receiverCandidates.first() else null
        receiverParameter?.let { adjustedParameters.remove(it) }

        var descriptor = createDescriptor(
            suggestFunctionNames(returnType),
            data.getDefaultVisibility(),
            adjustedParameters.toList(),
            receiverParameter,
            paramsInfo.typeParameters.sortedBy { it.originalDeclaration.name!! },
            paramsInfo.replacementMap,
            if (messages.isEmpty()) controlFlow else controlFlow.toDefault(),
            returnType,
        )
        return AnalysisResult(
            descriptor,
            if (messages.isEmpty()) Status.SUCCESS else Status.NON_CRITICAL_ERROR,
            messages
        )
    }

    private fun ControlFlow<KotlinType>.toDefault(): ControlFlow<KotlinType> =
        copy(outputValues = outputValues.filterNot { it is Jump || it is ExpressionValue })

    private fun suggestFunctionNames(returnType: KotlinType): List<String> {
        val functionNames = LinkedHashSet<String>()

        val validatorTarget = if (data.options.extractAsProperty) KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
        else KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION

        val targetSibling = data.targetSibling
        val container = targetSibling as KtElement
        val validator = KotlinNameValidatorProvider.getInstance()
            .createNameValidator(
                container = container,
                target = validatorTarget,
                anchor = if (targetSibling is KtAnonymousInitializer) container else targetSibling,
            )

        functionNames.addAll(
            nameSuggester.suggestNamesByType(
                returnType,
                data.commonParent,
                validator
            )
        )

        data.expressions.singleOrNull()?.let { expr ->
            val property = expr.getStrictParentOfType<KtProperty>()
            if (property?.initializer == expr) {
                property.name?.let { functionNames.add(KotlinNameSuggester.suggestNameByName(JvmAbi.getterName(it), validator)) }
            }
        }

        return functionNames.toList().takeIf { it.isNotEmpty() } ?: listOf(Registry.stringValue("kotlin.extract.function.default.name"))
    }
}
