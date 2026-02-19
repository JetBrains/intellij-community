// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult.ErrorMessage
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.ExpressionValue
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.Initializer
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.Jump
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.ParameterUpdate
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

object ControlFlowBuilder {
    fun <KotlinType> analyzeControlFlow(
        data: IExtractionData,
        outputDescriptor: OutputDescriptor<KotlinType>,
        modifiedVarDescriptors: Map<String, List<KtExpression>>,
        parameters: Set<IParameter<KotlinType>>,
        localVariablesToCopy: List<KtProperty>,
        typeDescriptor: TypeDescriptor<KotlinType>
    ): Pair<ControlFlow<KotlinType>, ErrorMessage?> {
        return outputDescriptor.analyzeControlFlow(
            data,
            modifiedVarDescriptors,
            typeDescriptor,
            parameters,
            localVariablesToCopy
        )
    }

    private fun <KotlinType> OutputDescriptor<KotlinType>.analyzeControlFlow(
        data: IExtractionData,
        modifiedVarDescriptors: Map<String, List<KtExpression>>,
        typeDescriptor: TypeDescriptor<KotlinType>,
        parameters: Set<IParameter<KotlinType>>,
        localVariablesToCopy: List<KtProperty>
    ): Pair<ControlFlow<KotlinType>, ErrorMessage?> {
        with(typeDescriptor) {
            val emptyControlFlow =
                ControlFlow<KotlinType>(emptyList(), {
                    object : OutputValueBoxer.AsTuple<KotlinType>(it) {
                        override val returnType: KotlinType by lazy {
                            if (outputValues.isEmpty() && lastExpressionHasNothingType) {
                                nothingType
                            } else createTuple(outputValues)
                        }
                    }
                }, localVariablesToCopy)

            val defaultReturnType = if (returnValueType.isMeaningful()) returnValueType else typeOfDefaultFlow
            if (defaultReturnType.isError()) return emptyControlFlow to ErrorMessage.ERROR_TYPES

            val controlFlow = if (defaultReturnType.isMeaningful()) {
                emptyControlFlow.copy(
                    outputValues = listOf(
                        ExpressionValue(
                            callSiteReturn = false,
                            hasImplicitReturn = false,
                            originalExpressions = listOfNotNull(defaultResultExpression),
                            valueType = defaultReturnType
                        )
                    )
                )
            } else emptyControlFlow

            val outParameters =
                parameters.filter { it.mirrorVarName != null && modifiedVarDescriptors[it.name] != null }
                    .sortedBy { it.nameForRef }
            val outDeclarations =
                localVariablesToCopy.filter { modifiedVarDescriptors[it.name!!] != null }
            val modifiedValueCount = outParameters.size + outDeclarations.size

            val outputValues = mutableListOf<OutputValue<KotlinType>>()

            val multipleExitsError = controlFlow to ErrorMessage.MULTIPLE_EXIT_POINTS
            val outputAndExitsError = controlFlow to ErrorMessage.OUTPUT_AND_EXIT_POINT

            if (typeOfDefaultFlow.isMeaningful()) {
                if (valuedReturnExpressions.isNotEmpty() || jumpExpressions.isNotEmpty()) return multipleExitsError

                outputValues.add(
                    ExpressionValue(
                        false,
                        hasImplicitReturn = false,
                        originalExpressions = listOfNotNull(defaultResultExpression),
                        valueType = typeOfDefaultFlow
                    )
                )
            } else if (valuedReturnExpressions.isNotEmpty()) {
                if (jumpExpressions.isNotEmpty()) return multipleExitsError

                if (defaultResultExpression != null) {
                    if (modifiedValueCount != 0) return outputAndExitsError
                    if (valuedReturnExpressions.size != 1) {
                        return multipleExitsError
                    }

                    val element = valuedReturnExpressions.first()
                    return controlFlow.copy(
                        outputValues = listOf(
                            Jump(
                                listOf(element),
                                element,
                                true,
                                booleanType,
                                unitType
                            )
                        )
                    ) to null
                }

                if (!hasSingleTarget) return multipleExitsError
                outputValues.add(ExpressionValue(true,
                                                 hasImplicitReturn = implicitReturn != null,
                                                 originalExpressions = valuedReturnExpressions,
                                                 valueType = returnValueType
                ))
            }

            outDeclarations.mapTo(outputValues) {
                Initializer(it, returnType(it) ?: nullableAnyType)
            }
            outParameters.mapTo(outputValues) { ParameterUpdate(it, modifiedVarDescriptors[it.name]!!) }

            if (outputValues.isNotEmpty()) {
                if (jumpExpressions.isNotEmpty() || valuedReturnExpressions.isNotEmpty() && outDeclarations.isNotEmpty()) return outputAndExitsError

                val boxerFactory: (List<OutputValue<KotlinType>>) -> OutputValueBoxer<KotlinType> = when {
                    outputValues.size > 3 -> {
                        if (!data.options.enableListBoxing) {
                            val outValuesStr =
                                (outParameters.map { typeDescriptor.renderForMessage(it) }
                                        + outDeclarations.map { typeDescriptor.renderForMessage(it)!! }).sorted()
                            return controlFlow to ErrorMessage.MULTIPLE_OUTPUT.addAdditionalInfo(outValuesStr)
                        }
                        { values ->
                            object : OutputValueBoxer.AsList<KotlinType>(values) {
                                override val returnType: KotlinType by lazy {
                                    assert(outputValues.isNotEmpty())
                                    createListType(outputValues.map { it.valueType })
                                }
                            }
                        }
                    }

                    else -> controlFlow.boxerFactory
                }

                return controlFlow.copy(outputValues = outputValues, boxerFactory = boxerFactory) to null
            }

            if (jumpExpressions.isNotEmpty()) {
                if (!hasSingleTarget) return multipleExitsError

                val conditional = !sameExitForDefaultAndJump && defaultResultExpression != null

                val elementToInsertAfterCall = if (sameExitForDefaultAndJump) null else jumpExpressions.first()
                return controlFlow.copy(
                    outputValues = listOf(
                        Jump(
                            jumpExpressions,
                            elementToInsertAfterCall,
                            conditional,
                            booleanType,
                            unitType
                        )
                    )
                ) to null
            }

            return controlFlow to null
        }
    }
}