// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.util.CanonicalTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asPsiType
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.functionTypeKind
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
 import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeMappingMode
import org.jetbrains.kotlin.builtins.StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Companion.suggestNameByName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

object ChangeSignatureFixFactory {

    enum class ChangeType {
        ADD, TOO_MANY_ARGUMENTS_WITH_TYPE_MISMATCH, REMOVE, TYPE_MISMATCH, CHANGE_FUNCTIONAL
    }

    private data class ParameterInfo(val name: String, val type: String)

    private data class Input(
        val type: ChangeType,
        val name: String,
        val isConstructor: Boolean,
        val idx: Int,
        val parameterCount: Int,
        val expectedParameterTypes: List<ParameterInfo>? = null,
    ) : KotlinApplicatorBasedQuickFix.Input

    private class ParameterQuickFix(
        element: PsiElement,
        input: ChangeSignatureFixFactory.Input,
    ) : KotlinApplicatorBasedQuickFix<PsiElement, Input>(element, input) {

        override fun getFamilyName(): String = KotlinBundle.message("fix.change.signature.family")

        override fun getActionName(
            element: PsiElement,
            input: ChangeSignatureFixFactory.Input,
        ): String = ChangeSignatureFixFactory.getActionName(element, input)

        override fun invoke(
            element: PsiElement,
            input: ChangeSignatureFixFactory.Input,
            project: Project,
            editor: Editor?,
        ) {
            val changeInfo = analyzeInModalWindow(element as KtElement, KotlinBundle.message("fix.change.signature.prepare")) {
                prepareChangeInfo(element, input)
            } ?: return

            when (changeInfo) {
                is KotlinChangeInfo -> KotlinChangeSignatureProcessor(project, changeInfo).run()
                is JavaChangeInfo -> ChangeSignatureProcessor(project, changeInfo).run()
            }

        }

        override fun startInWriteAction(): Boolean = false
    }

    val addParameterFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.TooManyArguments ->
        if (!isWritable(diagnostic.function)) return@IntentionBased emptyList()
        createAddParameterFix(diagnostic.function, diagnostic.psi)
    }

    val removeParameterFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NoValueForParameter ->
        createRemoveParameterFix(diagnostic.psi, diagnostic.violatedParameter.asString())
    }

    val typeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        createMismatchParameterTypeFix(diagnostic.psi, diagnostic.expectedType)
    }

    val nullForNotNullFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NullForNonnullType ->
        createMismatchParameterTypeFix(diagnostic.psi, diagnostic.expectedType)
    }

    private fun isWritable(symbol: KaSymbol): Boolean {
        return symbol.origin == KaSymbolOrigin.SOURCE || symbol.origin == KaSymbolOrigin.JAVA_SOURCE || symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED && symbol is KaConstructorSymbol
    }

    private fun getActionName(psi: PsiElement, input: Input): String {
        if (input.type == ChangeType.REMOVE) {
            return KotlinBundle.message("fix.change.signature.remove.parameter", input.name)
        }

        val valueArgument = psi.parentOfType<KtValueArgument>(true) ?: return ""
        val callElement = valueArgument.parentOfType<KtCallElement>() ?: return ""

        val arguments = callElement.valueArguments
        val newParametersCnt = arguments.size - input.parameterCount
        val isConstructor = input.isConstructor

        val declarationName = input.name
        return when (input.type) {
            ChangeType.CHANGE_FUNCTIONAL -> KotlinBundle.message("fix.change.signature.lambda")

            ChangeType.ADD -> {
                if (newParametersCnt <= 0) return ""
                KotlinBundle.message(
                    if (isConstructor) "fix.add.function.parameters.add.parameter.generic.constructor" else "fix.add.function.parameters.add.parameter.generic.function",
                    newParametersCnt,
                    declarationName
                )
            }

            ChangeType.TOO_MANY_ARGUMENTS_WITH_TYPE_MISMATCH -> {
                KotlinBundle.message(
                    if (isConstructor) "fix.add.function.parameters.change.signature.constructor" else "fix.add.function.parameters.change.signature.function",
                    declarationName
                )
            }

            ChangeType.TYPE_MISMATCH -> {
                if (newParametersCnt <= 0) return ""
                KotlinBundle.message(
                    if (isConstructor) "fix.add.function.parameters.add.parameter.constructor" else "fix.add.function.parameters.add.parameter.function",
                    input.idx + 1,
                    newParametersCnt,
                    declarationName
                )
            }

            ChangeType.REMOVE -> error("Unreachable, already processed above")
        }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun prepareChangeInfo(psi: PsiElement, input: Input): ChangeInfo? {
        if (input.type == ChangeType.CHANGE_FUNCTIONAL) {
            return prepareFunctionalLiteralChangeInfo(psi as KtLambdaExpression, input)
        }
        val callElement = psi.parentOfType<KtCallElement>(input.type == ChangeType.REMOVE) ?: return null
        val functionCall =
            ((callElement.resolveToCall() as? KaErrorCallInfo)?.candidateCalls?.firstOrNull() as? KaCallableMemberCall<*, *>)
                ?: return null

        val psiElement = functionCall.partiallyAppliedSymbol.symbol.psi

        if (psiElement is PsiMethod) {
            val result = psiElement.parameterList.parameters.mapIndexed { idx, param ->
                ParameterInfoImpl.create(idx)
                    .withName(param.getName())
                    .withType(param.getType())
            }.toMutableList()


            if (input.type == ChangeType.REMOVE) {
                result.removeAt(input.idx)
            } else {
                val currentArgument = psi.parentOfType<KtValueArgument>(true) ?: return null

                val usedNames = mutableSetOf<String>()

                fun getNewParameterInfo(currentArgument: KtValueArgument): ParameterInfoImpl? {
                    val paramName = getNewArgumentName(currentArgument) { usedNames.add(it) }
                    val kaType = getKtType(currentArgument.getArgumentExpression())
                    val paramType = kaType?.asPsiType(callElement, false, KaTypeMappingMode.VALUE_PARAMETER) ?: return null
                    return ParameterInfoImpl.createNew()
                        .withName(paramName)
                        .withType(paramType)
                        .withDefaultValue(currentArgument.text)
                }

                if (input.type == ChangeType.TYPE_MISMATCH) {
                    val parameterInfo = getNewParameterInfo(currentArgument) ?: return null
                    result.add(input.idx, parameterInfo)
                } else {
                    val parameters = psiElement.parameterList.parameters
                    val arguments = callElement.valueArguments

                    for (i in arguments.indices) {
                        val argument = arguments[i]
                        val expression = argument.getArgumentExpression() ?: continue

                        if (i < parameters.size) {
                            usedNames.add(parameters[i].name)
                            val argumentType = getKtType(expression)?.asPsiType(callElement, false, KaTypeMappingMode.VALUE_PARAMETER)
                            if (argumentType != null && !TypeConversionUtil.isAssignable(parameters[i].type, argumentType)) {
                                result[i].setType(argumentType)
                            }
                        } else {
                            getNewParameterInfo(argument as KtValueArgument)?.let {
                                result.add(it)
                            }
                        }
                    }
                }
            }
            val changeInfo = JavaChangeInfoImpl.generateChangeInfo(
                psiElement, false, true, null,
                psiElement.name, psiElement.returnType?.let { CanonicalTypes.createTypeWrapper(it) },
                result.toTypedArray(),
                null, emptySet(), emptySet()
            )
            return changeInfo
        }

        val ktCallableDeclaration = psiElement as? KtNamedDeclaration ?: return null

        val changeInfo = KotlinChangeInfo(KotlinMethodDescriptor(ktCallableDeclaration))
        val index = input.idx + if ((ktCallableDeclaration as? KtCallableDeclaration)?.receiverTypeReference != null) 1 else 0
        if (input.type == ChangeType.REMOVE) {
            assert(index >= 0)
            changeInfo.removeParameter(index)
        } else {
            val usedNames = mutableSetOf<String>()
            val validator = getNameValidator(ktCallableDeclaration, usedNames)

            fun getNewParameterInfo(currentArgument: ValueArgument): KotlinParameterInfo? {
                val argumentExpression = currentArgument.getArgumentExpression()
                val ktType = getKtType(argumentExpression) ?: return null
                return KotlinParameterInfo(
                    originalType = KotlinTypeInfo(ktType, callElement),
                    name = getNewArgumentName(currentArgument, validator),
                    originalIndex = -1,
                    valOrVar = defaultValOrVar(ktCallableDeclaration),
                    defaultValueForCall = argumentExpression?.copy() as KtExpression?,
                    defaultValueAsDefaultParameter = false,
                    defaultValue = null,
                    context = ktCallableDeclaration
                )
            }

            val currentArgument = psi.parentOfType<KtValueArgument>(true) ?: return null
            if (input.type == ChangeType.TYPE_MISMATCH) {
                val parameterInfo = getNewParameterInfo(currentArgument) ?: return null
                changeInfo.addParameter(parameterInfo, index)
            } else {
                val parameters = (ktCallableDeclaration as? KtCallableDeclaration)?.valueParameters ?: emptyList()
                val arguments = callElement.valueArguments

                for (i in arguments.indices) {
                    val argument = arguments[i]
                    val expression = argument.getArgumentExpression() ?: continue

                    if (i < parameters.size) {
                        usedNames.add(parameters[i].name!!)
                        val argumentType = getKtType(expression)
                        val parameterType = parameters[i].returnType
                        if (argumentType != null && !argumentType.isSubtypeOf(parameterType)) {
                            changeInfo.newParameters[i + if ((ktCallableDeclaration as? KtCallableDeclaration)?.receiverTypeReference != null) 1 else 0].setType(
                                argumentType.render(position = Variance.IN_VARIANCE)
                            )
                        }
                    } else {
                        getNewParameterInfo(argument)?.let {
                            changeInfo.addParameter(it)
                        }
                    }
                }
            }
        }

        return changeInfo
    }

    context(_: KaSession)
    private fun prepareFunctionalLiteralChangeInfo(psi: KtLambdaExpression, input: Input): KotlinChangeInfo? {
        val callable = psi.functionLiteral
        val descriptor = KotlinMethodDescriptor(callable)
        val paramInfos = input.expectedParameterTypes
        if (paramInfos == null) {
            return null
        }

        val changeInfo = KotlinChangeInfo(descriptor)
        changeInfo.clearParameters()

        val nameValidator = getNameValidator(callable)

        for (paramInfo in paramInfos.dropLast(1)) {
            val paramName = paramInfo.name
            changeInfo.addParameter(
                KotlinParameterInfo(
                    originalIndex = -1,
                    originalType = KotlinTypeInfo(paramInfo.type, callable),
                    name = suggestNameByName(paramName, nameValidator),
                    valOrVar = KotlinValVar.None,
                    defaultValueForCall = null,
                    defaultValueAsDefaultParameter = false,
                    defaultValue = null,
                    context = callable
                )
            )
        }

        changeInfo.newReturnTypeInfo = KotlinTypeInfo(paramInfos.last().type, callable)
        return changeInfo
    }

    context(_: KaSession)
    private fun getNameValidator(
        callable: KtNamedDeclaration, usedNames: MutableSet<String> = mutableSetOf<String>()
    ): (String) -> Boolean {
        val nameValidator = KotlinDeclarationNameValidator(
          callable,
          true,
          KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
          listOf(callable),
        )
        return { name -> usedNames.add(name) && nameValidator.validate(name) }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getKtType(argumentExpression: KtExpression?): KaType? {
        val ktType = argumentExpression?.expressionType
        return ktType?.toFunctionType() ?: ktType
    }


    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun KaType.toFunctionType(): KaType? {
        val typeKind = functionTypeKind
        when (typeKind) {
            FunctionTypeKind.KFunction -> typeKind.nonReflectKind()
            FunctionTypeKind.KSuspendFunction -> typeKind.nonReflectKind()
            else -> null
        }?.let {
            val functionalType = this as KaFunctionType
            return buildClassType(it.numberedClassId((functionalType).arity)) {
                functionalType.parameterTypes.forEach { arg ->
                    argument(arg)
                }
                argument(functionalType.returnType)
            }
        }
        return null
    }

    context(_: KaSession)
    private fun getNewArgumentName(argument: ValueArgument, validator: (String) -> Boolean): String {
        val expression = KtPsiUtil.deparenthesize(argument.getArgumentExpression())
        val argumentName = argument.getArgumentName()?.asName?.asString() ?: (expression as? KtNameReferenceExpression)?.getReferencedName()
            ?.takeIf { !isSpecialName(it) }

        return when {
            argumentName != null -> suggestNameByName(argumentName, validator)
            expression != null -> {
                val expressionText = expression.text
                with(KotlinNameSuggester()) {
                    if (isSpecialName(expressionText)) {
                        val ktType = expression.expressionType
                        if (ktType != null) {
                            return suggestTypeNames(ktType).map { typeName ->
                                suggestNameByName(typeName, validator)
                            }.firstOrNull() ?: "param"
                        }
                    }

                    suggestExpressionNames(expression, validator).firstOrNull() ?: "param"
                }
            }

            else -> suggestNameByName("param", validator)
        }
    }

    private fun isSpecialName(name: String): Boolean {
        return name == IMPLICIT_LAMBDA_PARAMETER_NAME.identifier || name == "field"
    }

    context(_: KaSession)
    private fun createAddParameterFix(
        ktCallableSymbol: KaCallableSymbol,
        element: PsiElement,
    ): List<ParameterQuickFix> {
        if (ktCallableSymbol !is KaFunctionSymbol) return emptyList()
        val name = getDeclarationName(ktCallableSymbol) ?: return emptyList()
        val valueArgument = element.parentOfType<KtValueArgument>(true) ?: return emptyList()
        val callElement = valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
        val valueArguments = callElement.valueArguments
        val idx = valueArguments.indexOf(valueArgument)
        val hasTypeMismatch = idx > 0 && valueArguments.take(idx).zip(ktCallableSymbol.valueParameters).any { (arg, s) ->
            (arg as? KtValueArgument)?.getArgumentExpression()?.expressionType?.isSubtypeOf(s.returnType) != true
        }

        val input = Input(
            type = if (hasTypeMismatch) ChangeType.TOO_MANY_ARGUMENTS_WITH_TYPE_MISMATCH else ChangeType.ADD,
            name = name,
            isConstructor = ktCallableSymbol is KaConstructorSymbol,
            idx = idx,
            parameterCount = ktCallableSymbol.valueParameters.size,
        )
        return listOf(
            ParameterQuickFix(element, input),
        )
    }

    context(_: KaSession)
    private fun createRemoveParameterFix(
        element: KtElement,
        parameterName: String,
    ): List<ParameterQuickFix> {
        val functionLikeSymbol =
            ((element.resolveToCall() as? KaErrorCallInfo)?.candidateCalls?.firstOrNull() as? KaCallableMemberCall<*, *>)?.symbol as? KaFunctionSymbol
                ?: return emptyList()

        if (!isWritable(functionLikeSymbol)) return emptyList()
        if (functionLikeSymbol is KaNamedFunctionSymbol && functionLikeSymbol.valueParameters.any { it.isVararg } ||
            functionLikeSymbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED ||
            functionLikeSymbol.origin == KaSymbolOrigin.LIBRARY
        ) return emptyList()

        val callee = (element as? KtDotQualifiedExpression)?.selectorExpression ?: element
        if (callee.parentOfType<KtCallElement>(true) == null) return emptyList()

        val input = Input(
            type = ChangeType.REMOVE,
            name = parameterName,
            isConstructor = functionLikeSymbol is KaConstructorSymbol,
            idx = functionLikeSymbol.valueParameters.indexOfFirst { it.name.asString() == parameterName },
            parameterCount = functionLikeSymbol.valueParameters.size,
        )
        return listOf(
            ParameterQuickFix(callee, input),
        )
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createMismatchParameterTypeFix(
        element: PsiElement,
        expectedType: KaType
    ): List<ParameterQuickFix> {
        val valueArgument = element.getStrictParentOfType<KtValueArgument>()
        if (valueArgument == null) return emptyList()

        val callElement = valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
        val functionLikeSymbol =
            ((callElement.resolveToCall() as? KaErrorCallInfo)?.candidateCalls?.firstOrNull() as? KaCallableMemberCall<*, *>)?.symbol as? KaFunctionSymbol
                ?: return emptyList()

        if (!isWritable(functionLikeSymbol)) return emptyList()

        val name = getDeclarationName(functionLikeSymbol) ?: return emptyList()

        val newParametersCnt = callElement.valueArguments.size - functionLikeSymbol.valueParameters.size

        if (newParametersCnt <= 0 && element !is KtLambdaExpression) return emptyList()

        val expectedParameterTypes = (((expectedType as? KaFlexibleType)?.upperBound ?: expectedType) as? KaFunctionType)
            ?.let { it.parameterTypes + it.returnType }
            ?.map {
                ParameterInfo(
                    KotlinNameSuggester().suggestTypeNames(it).firstOrNull() ?: "param",
                    it.render(position = Variance.IN_VARIANCE)
                )
            }

        val input = Input(
            type = if (element is KtLambdaExpression) ChangeType.CHANGE_FUNCTIONAL else ChangeType.TYPE_MISMATCH,
            name = name,
            isConstructor = functionLikeSymbol is KaConstructorSymbol,
            idx = callElement.valueArguments.indexOf(valueArgument),
            parameterCount = functionLikeSymbol.valueParameters.size,
            expectedParameterTypes = expectedParameterTypes,
        )

        return listOf(
            ParameterQuickFix(element, input),
        )
    }
}

context(_: KaSession)
internal fun getDeclarationName(functionLikeSymbol: KaFunctionSymbol): String? {
    return when(functionLikeSymbol) {
        is KaConstructorSymbol -> {
            val constructorSymbol = functionLikeSymbol
            if ((constructorSymbol.containingDeclaration as? KaNamedClassSymbol)?.isInline == true) {
                null
            } else constructorSymbol.containingClassId?.shortClassName
        }

        is KaNamedFunctionSymbol -> {
            functionLikeSymbol.name
        }

        else -> null
    }?.asString()
}