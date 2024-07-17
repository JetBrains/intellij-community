// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.suggested

import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.suggested.defaultValue
import org.jetbrains.kotlin.idea.refactoring.suggested.modifiers
import org.jetbrains.kotlin.idea.refactoring.suggested.receiverType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.utils.addIfNotNull

class KotlinSuggestedRefactoringExecution(
    refactoringSupport: KotlinSuggestedRefactoringSupport
) : SuggestedRefactoringExecution(refactoringSupport) {

    private data class PrepareChangeSignatureResult(
        val returnTypeInfo: KotlinTypeInfo,
        val parameterTypeInfos: List<KotlinTypeInfo>
    )

    override fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any {
        val declaration = data.declaration as KtCallableDeclaration
        return analyzeInModalWindow(declaration, KotlinBundle.message("fix.change.signature.prepare")) {
            val descriptorWithNewSignature = declaration.symbol as KaCallableSymbol
            val newSignature = data.newSignature

            val returnType = descriptorWithNewSignature.returnType
            val returnTypeInfo = if (returnType !is KaErrorType) {
                KotlinTypeInfo(ktType = returnType, context = declaration)
            } else {
                KotlinTypeInfo(text = newSignature.type, context = declaration)
            }

            val valueParameters = (descriptorWithNewSignature as? KaFunctionSymbol)?.valueParameters
            require((valueParameters?.size ?: 0) == newSignature.parameters.size) {
                "Number of parameters of newSignature is ${newSignature.parameters.size} and of the descriptor is ${valueParameters?.size ?: 0}"
            }

            val parameterTypeInfos = if (valueParameters != null)
                descriptorWithNewSignature.valueParameters.zip(newSignature.parameters)
                    .map { (parameterDescriptor, parameterData) ->
                        val type = parameterDescriptor.returnType
                        if (type !is KaErrorType) {
                            KotlinTypeInfo(type, declaration)
                        } else {
                            KotlinTypeInfo(text = parameterData.type, declaration)
                        }
                    } else emptyList()

            PrepareChangeSignatureResult(returnTypeInfo, parameterTypeInfos)
        }
    }

    override fun performChangeSignature(
        data: SuggestedChangeSignatureData,
        newParameterValues: List<NewParameterValue>,
        preparedData: Any?
    ) {
        val (returnTypeInfo, parameterTypeInfos) = preparedData as PrepareChangeSignatureResult

        val declaration = data.declaration as KtCallableDeclaration
        val project = declaration.project

        val methodDescriptor = KotlinMethodDescriptor(declaration)
        val parameters = mutableListOf<KotlinParameterInfo>()
        var newParameterValueIndex = 0

        val receiver: KotlinParameterInfo? = data.newSignature.receiverType?.let { newReceiverType ->
            val newTypeInfo = KotlinTypeInfo(text = newReceiverType, declaration)
            if (data.oldSignature.receiverType != null) {
                methodDescriptor.receiver!!.apply {
                    setType(newReceiverType)
                }
            } else {
                KotlinParameterInfo(
                    originalIndex = -1,
                    originalType = newTypeInfo,
                    name = "",
                    valOrVar = KotlinValVar.None,
                    defaultValueForCall = null,
                    defaultValueAsDefaultParameter = false,
                    defaultValue = null,
                    context = declaration
                ).apply {
                    setNewParameterValue(newParameterValues[newParameterValueIndex++])
                }
            }
        }

        parameters.addIfNotNull(receiver)

        val psiFactory = KtPsiFactory(project)

        for ((index, parameter) in data.newSignature.parameters.withIndex()) {
            val initialIndex = data.oldSignature.parameterById(parameter.id)
                ?.let { data.oldSignature.parameterIndex(it) }

            val defaultValue = parameter.defaultValue?.let { psiFactory.createExpression(it) }

            val modifierList = parameter.modifiers
                .takeIf { it.isNotEmpty() }
                ?.let { psiFactory.createModifierList(it) }

            val parameterInfo = if (initialIndex == null) {
                KotlinParameterInfo(
                    name = parameter.name,
                    originalType = parameterTypeInfos[index],
                    originalIndex = -1,
                    valOrVar = KotlinValVar.None,
                    defaultValueForCall = null,
                    defaultValueAsDefaultParameter = false,
                    defaultValue = defaultValue,
                    modifierList = modifierList,
                    context = declaration
                ).apply {
                    // a few last added parameters may be missing in newParameterValues
                    // because they have default values and we did not offer to enter value for them
                    if (newParameterValueIndex < newParameterValues.size) {
                        setNewParameterValue(newParameterValues[newParameterValueIndex++])
                    }
                }
            } else {
                val initialIndexWithOffset = initialIndex + (if (methodDescriptor.receiver != null) 1 else 0)
                KotlinParameterInfo(
                    name = methodDescriptor.parameters[initialIndexWithOffset].name,
                    originalType = methodDescriptor.parameters[initialIndexWithOffset].originalType,
                    originalIndex = initialIndexWithOffset,
                    valOrVar = KotlinValVar.None,
                    defaultValueForCall = null,
                    defaultValueAsDefaultParameter = false,
                    defaultValue = defaultValue,
                    context = declaration
                ).apply {
                    currentType = parameterTypeInfos[index]
                    name = parameter.name
                }
            }
            parameters.add(parameterInfo)
        }

        val changeInfo = KotlinChangeInfo(
            methodDescriptor,
            name = data.newSignature.name,
            newReturnTypeInfo = returnTypeInfo,
            parameterInfos = parameters,
            receiver = receiver,
            checkUsedParameters = true,
        )

        KotlinChangeSignatureProcessor(project, changeInfo).run()
    }

    private fun KotlinParameterInfo.setNewParameterValue(value: NewParameterValue) {
        when (value) {
            is NewParameterValue.AnyVariable -> isUseAnySingleVariable = true
            is NewParameterValue.Expression -> defaultValueForCall = value.expression as KtExpression
            is NewParameterValue.None -> defaultValueForCall = null
        }
    }
}