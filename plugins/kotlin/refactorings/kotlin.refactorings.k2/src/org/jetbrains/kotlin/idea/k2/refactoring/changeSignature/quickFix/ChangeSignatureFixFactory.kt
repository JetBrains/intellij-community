// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtErrorCallInfo
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.builtins.StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Companion.suggestNameByName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.getStartLineOffset
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle.message
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.defaultValOrVar
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.types.Variance
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.indexOf
import kotlin.let
import kotlin.sequences.firstOrNull
import kotlin.sequences.map
import kotlin.takeIf

object ChangeSignatureFixFactory {

    class Input(val isConstructor: Boolean, val idx: Int, val parameterCount: Int, val name: String) : KotlinApplicatorInput

    @OptIn(KtAllowAnalysisOnEdt::class)
    private val applicator = applicator<PsiElement, Input> {
        familyName { message("fix.change.signature.family") }
        actionName { psi, input ->

            if (input.idx >= 0) {
                return@actionName message("fix.change.signature.remove.parameter", input.name)
            }

            val valueArgument = psi.parentOfType<KtValueArgument>(true) ?: return@actionName ""
            val callElement = valueArgument.parentOfType<KtCallElement>() ?: return@actionName ""

            val arguments = callElement.valueArguments
            val newParametersCnt = arguments.size - input.parameterCount
            assert(newParametersCnt > 0)

            val isConstructor = input.isConstructor

            val declarationName = input.name

            val argumentIndex = callElement.valueArguments.indexOf(valueArgument)
            if (isConstructor) {
                message(
                    //"fix.add.function.parameters.add.parameter.constructor",
                    "fix.add.function.parameters.add.parameter.generic.constructor",
                    //argumentIndex,
                    newParametersCnt, declarationName
                )
            } else {
                message(
                    //"fix.add.function.parameters.add.parameter.function",
                    "fix.add.function.parameters.add.parameter.generic.function",
                    newParametersCnt, declarationName
                )
            }
        }
        applyTo { psi, input ->

            val callElement = psi.parentOfType<KtCallElement>(input.idx >= 0) ?: return@applyTo
            val changeInfo = allowAnalysisOnEdt {
                analyze(callElement) {
                    val functionCall = ((callElement.resolveCall() as? KtErrorCallInfo)?.candidateCalls?.firstOrNull() as? KtCallableMemberCall<*, *>) ?: return@analyze null

                    val ktCallableDeclaration = functionCall.partiallyAppliedSymbol.symbol.psi as? KtCallableDeclaration ?: return@analyze null //todo change java from kotlin

                    val methodDescriptor = KotlinMethodDescriptor(ktCallableDeclaration)

                    if (input.idx >= 0) {
                        methodDescriptor.parameters.removeAt(input.idx)
                    } else {
                        val argument = psi.parentOfType<KtValueArgument>(true) ?: return@analyze null
                        val argumentExpression = argument.getArgumentExpression()
                        val ktType = getKtType(argumentExpression) ?: return@analyze null

                        val nameValidator = KotlinDeclarationNameValidator(
                            ktCallableDeclaration,
                            true,
                            KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                            this,
                        )
                        val parameterInfo = KotlinParameterInfo(
                            originalType = KotlinTypeInfo(ktType.render(position = Variance.IN_VARIANCE), callElement),
                            name = getNewArgumentName(argument, nameValidator),
                            originalIndex = -1,
                            valOrVar = defaultValOrVar(ktCallableDeclaration),
                            defaultValueForCall = argumentExpression?.copy() as KtExpression?,
                            defaultValueAsDefaultParameter = false,
                            defaultValue = null,
                            context = ktCallableDeclaration
                        )
                        methodDescriptor.parameters.add(
                            callElement.valueArguments.indexOf(argument),
                            parameterInfo
                        )
                    }

                    KotlinChangeInfo(methodDescriptor)
                }
            } ?: return@applyTo
            KotlinChangeSignatureProcessor(changeInfo.method.project, changeInfo).run()
        }
        startInWriteAction { false }
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
    private fun getNewArgumentName(argument: KtValueArgument, validator: (String) -> Boolean): String {
        val expression = KtPsiUtil.deparenthesize(argument.getArgumentExpression())
        val argumentName = argument.getArgumentName()?.asName?.asString()
            ?: (expression as? KtNameReferenceExpression)?.getReferencedName()?.takeIf { !isSpecialName(it) }

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

    val addParameterFactory =
        diagnosticFixFactory(KtFirDiagnostic.TooManyArguments::class, applicator) { diagnostic ->
            createAddParameterFix(diagnostic.function, diagnostic.psi)
        }

    context(KtAnalysisSession)
    private fun createAddParameterFix(ktCallableSymbol: KtCallableSymbol, psi: PsiElement): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        if (ktCallableSymbol !is KtFunctionLikeSymbol) return emptyList()
        val isConstructor = ktCallableSymbol is KtConstructorSymbol
        val name = when {
            isConstructor -> {
                val constructorSymbol = ktCallableSymbol as KtConstructorSymbol
                if ((constructorSymbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol)?.isInline == true) {
                    return emptyList()
                }
                (constructorSymbol).containingClassIdIfNonLocal?.shortClassName
            }
            else -> {
                (ktCallableSymbol as KtFunctionSymbol).name
            }
        }?.asString() ?: return emptyList()
        val valueArgument = psi.parentOfType<KtValueArgument>(true) ?: return emptyList()
        valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
        return listOf(psi withInput Input(isConstructor, -1, ktCallableSymbol.valueParameters.size, name))
    }

    val removeParameterFactory =
        diagnosticFixFactory(KtFirDiagnostic.NoValueForParameter::class, applicator) { diagnostic ->
            createRemoveParameterFix(diagnostic.violatedParameter, diagnostic.psi)
        }

    context(KtAnalysisSession)
    private fun createRemoveParameterFix(symbol: KtSymbol, psi: PsiElement): List<KotlinApplicatorTargetWithInput<PsiElement, Input>> {
        if (symbol !is KtParameterSymbol) return emptyList()
        val containingSymbol = symbol.getContainingSymbol() as? KtFunctionLikeSymbol ?: return emptyList()
        val isConstructor = containingSymbol is KtConstructorSymbol
        val name = (symbol as? KtNamedSymbol)?.name?.asString() ?: return emptyList()
        psi.parentOfType<KtCallElement>(true) ?: return emptyList()
        val idx = containingSymbol.valueParameters.indexOf(symbol)
        return listOf(psi withInput Input(isConstructor, idx, containingSymbol.valueParameters.size, name))
    }
}