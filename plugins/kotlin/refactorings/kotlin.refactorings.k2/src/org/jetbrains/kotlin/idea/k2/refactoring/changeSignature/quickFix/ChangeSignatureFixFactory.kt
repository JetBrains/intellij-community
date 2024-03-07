// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtErrorCallInfo
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.builtins.StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Companion.suggestNameByName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
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
    ) : KotlinApplicatorInput

    private val applicator = object : KotlinApplicator.PsiBased<PsiElement, Input> {

        override fun getFamilyName(): String = KotlinBundle.message("fix.change.signature.family")

        override fun getActionName(
            psi: PsiElement,
            input: Input,
        ): String = ChangeSignatureFixFactory.getActionName(psi, input)

        override fun applyTo(
            psi: PsiElement,
            input: Input,
            project: Project,
            editor: Editor?,
        ) {
            val changeInfo = analyzeInModalWindow(psi as KtElement, KotlinBundle.message("fix.change.signature.prepare")) {
                prepareChangeInfo(psi, input)
            } ?: return

            KotlinChangeSignatureProcessor(changeInfo.method.project, changeInfo).run()
        }

        override fun startInWriteAction(): Boolean = false
    }

    val addParameterFactory = diagnosticFixFactory(KtFirDiagnostic.TooManyArguments::class, applicator) { diagnostic ->
        createAddParameterFix(diagnostic.function, diagnostic.psi)
    }

    val removeParameterFactory = diagnosticFixFactory(KtFirDiagnostic.NoValueForParameter::class, applicator) { diagnostic ->
        createRemoveParameterFix(diagnostic.violatedParameter, diagnostic.psi)
    }

    val typeMismatchFactory = diagnosticFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class, applicator) { diagnostic ->
        createMismatchParameterTypeFix(diagnostic.psi, diagnostic.expectedType)
    }

    val nullForNotNullFactory = diagnosticFixFactory(KtFirDiagnostic.NullForNonnullType::class, applicator) { diagnostic ->
        createMismatchParameterTypeFix(diagnostic.psi, diagnostic.expectedType)
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
                assert(newParametersCnt > 0)
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

    context(KtAnalysisSession)
    private fun prepareChangeInfo(psi: PsiElement, input: Input): KotlinChangeInfo? {
        if (input.type == ChangeType.CHANGE_FUNCTIONAL) {
            return prepareFunctionalLiteralChangeInfo(psi as KtLambdaExpression, input)
        }
        val callElement = psi.parentOfType<KtCallElement>(input.type == ChangeType.REMOVE) ?: return null
        val functionCall =
            ((callElement.resolveCall() as? KtErrorCallInfo)?.candidateCalls?.firstOrNull() as? KtCallableMemberCall<*, *>)
                ?: return null

        val ktCallableDeclaration = functionCall.partiallyAppliedSymbol.symbol.psi as? KtNamedDeclaration
            ?: return null //todo change java from kotlin

        val methodDescriptor = KotlinMethodDescriptor(ktCallableDeclaration)

        val changeInfo = KotlinChangeInfo(methodDescriptor)
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
                    originalType = KotlinTypeInfo(ktType.render(position = Variance.IN_VARIANCE), callElement),
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
                        val parameterType = parameters[i].getReturnKtType()
                        if (argumentType != null && !argumentType.isSubTypeOf(parameterType)) {
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

    context(KtAnalysisSession)
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
                    -1,
                    KotlinTypeInfo(paramInfo.type, callable),
                    suggestNameByName(paramName, nameValidator),
                    KotlinValVar.None,
                    null,
                    false,
                    null,
                    callable
                )
            )
        }

        changeInfo.newReturnTypeInfo = KotlinTypeInfo(paramInfos.last().type, callable)
        return changeInfo
    }

    context(KtAnalysisSession)
    private fun getNameValidator(
        callable: KtNamedDeclaration, usedNames: MutableSet<String> = mutableSetOf<String>()
    ): (String) -> Boolean {
        val nameValidator = KotlinDeclarationNameValidator(
            callable,
            true,
            KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
            this@KtAnalysisSession,
        )
        return { name -> usedNames.add(name) && nameValidator(name) }
    }

    context(KtAnalysisSession)
    private fun getKtType(argumentExpression: KtExpression?): KtType? {
        var ktType = argumentExpression?.getKtType()
        val typeKind = ktType?.functionTypeKind
        when (typeKind) {
            FunctionTypeKind.KFunction -> typeKind.nonReflectKind()
            FunctionTypeKind.KSuspendFunction -> typeKind.nonReflectKind()
            else -> null
        }?.let {
            val functionalType = ktType as KtFunctionalType
            return buildClassType(it.numberedClassId((functionalType).arity)) {
                functionalType.parameterTypes.forEach { arg ->
                    argument(arg)
                }
                argument(functionalType.returnType)
            }
        }
        return ktType
    }

    context(KtAnalysisSession)
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
                        val ktType = expression.getKtType()
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

    context(KtAnalysisSession)
    private fun createAddParameterFix(
        ktCallableSymbol: KtCallableSymbol, psi: PsiElement
    ): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        if (ktCallableSymbol !is KtFunctionLikeSymbol) return emptyList()
        val isConstructor = ktCallableSymbol is KtConstructorSymbol
        val name = getDeclarationName(ktCallableSymbol) ?: return emptyList()
        val valueArgument = psi.parentOfType<KtValueArgument>(true) ?: return emptyList()
        val callElement = valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
        val valueArguments = callElement.valueArguments
        val idx = valueArguments.indexOf(valueArgument)
        val hasTypeMismatch = idx > 0 && valueArguments.take(idx).zip(ktCallableSymbol.valueParameters).any { (arg, s) ->
            (arg as? KtValueArgument)?.getArgumentExpression()?.getKtType()?.isSubTypeOf(s.returnType) != true
        }
        return listOf(
            psi withInput Input(
                type = if (hasTypeMismatch) ChangeType.TOO_MANY_ARGUMENTS_WITH_TYPE_MISMATCH else ChangeType.ADD,
                name,
                isConstructor,
                idx,
                ktCallableSymbol.valueParameters.size
            )
        )
    }

    context(KtAnalysisSession)
    private fun createRemoveParameterFix(symbol: KtSymbol, psi: PsiElement): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        if (symbol !is KtParameterSymbol) return emptyList()
        val containingSymbol = symbol.getContainingSymbol() as? KtFunctionLikeSymbol ?: return emptyList()
        if (containingSymbol is KtFunctionSymbol && containingSymbol.valueParameters.any { it.isVararg } ||
            containingSymbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED ||
            containingSymbol.origin == KtSymbolOrigin.LIBRARY
        ) return emptyList()

        val isConstructor = containingSymbol is KtConstructorSymbol
        val name = (symbol as? KtNamedSymbol)?.name?.asString() ?: return emptyList()
        psi.parentOfType<KtCallElement>(true) ?: return emptyList()
        val idx = containingSymbol.valueParameters.indexOf(symbol)
        return listOf(
            psi withInput Input(
                type = ChangeType.REMOVE, name, isConstructor, idx, containingSymbol.valueParameters.size
            )
        )
    }

    context(KtAnalysisSession)
    private fun createMismatchParameterTypeFix(
        psi: PsiElement, expectedType: KtType
    ): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        val valueArgument = psi.getStrictParentOfType<KtValueArgument>()
        if (valueArgument == null) return emptyList()

        val callElement = valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
        val functionLikeSymbol =
            ((callElement.resolveCall() as? KtErrorCallInfo)?.candidateCalls?.firstOrNull() as? KtCallableMemberCall<*, *>)?.symbol as? KtFunctionLikeSymbol
                ?: return emptyList()

        val isConstructor = functionLikeSymbol is KtConstructorSymbol
        val name = getDeclarationName(functionLikeSymbol) ?: return emptyList()

        val newParametersCnt = callElement.valueArguments.size - functionLikeSymbol.valueParameters.size

        if (newParametersCnt <= 0 && psi !is KtLambdaExpression) return emptyList()

        val functionalType = expectedType as? KtFunctionalType
        val ktTypes = functionalType?.let { it.parameterTypes + it.returnType }
        return listOf(
            psi withInput Input(
                if (psi is KtLambdaExpression) {
                    ChangeType.CHANGE_FUNCTIONAL
                } else {
                    ChangeType.TYPE_MISMATCH
                },
                name,
                isConstructor,
                callElement.valueArguments.indexOf(valueArgument),
                functionLikeSymbol.valueParameters.size,
                ktTypes?.map {
                    ParameterInfo(
                        KotlinNameSuggester().suggestTypeNames(it).firstOrNull() ?: "param",
                        it.render(position = Variance.IN_VARIANCE)
                    )
                },
            )
        )
    }
}

context(KtAnalysisSession)
internal fun getDeclarationName(functionLikeSymbol: KtFunctionLikeSymbol): String? {
    return when(functionLikeSymbol) {
        is KtConstructorSymbol -> {
            val constructorSymbol = functionLikeSymbol
            if ((constructorSymbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol)?.isInline == true) {
                null
            } else constructorSymbol.containingClassIdIfNonLocal?.shortClassName
        }

        is KtFunctionSymbol -> {
            functionLikeSymbol.name
        }

        else -> null
    }?.asString()
}