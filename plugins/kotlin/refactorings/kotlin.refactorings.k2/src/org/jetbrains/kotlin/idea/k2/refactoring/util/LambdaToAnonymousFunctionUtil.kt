// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.types.Variance

object LambdaToAnonymousFunctionUtil {
    /**
     * Build function signature and body based on provided [lambda].
     * Returns null for function literals without body.
     *
     * @param forceNonNullReturnType when `true`, the return type is rendered as non-nullable
     *   provided that no nullable expressions are returned anywhere in the lambda body.
     *
     * NB: to perform required calculations, the whole file with the lambda expression is copied!
     * So it should not be used during highlighting or other non-explicitly started activities
     */
    @OptIn(KaExperimentalApi::class)
    fun prepareFunctionText(
        lambda: KtLambdaExpression,
        functionName: String = "",
        parameterNames: List<String> = emptyList(),
        forceNonNullReturnType: Boolean = false,
    ): String? {
        val functionLiteral = lambda.functionLiteral
        val psiFactory = KtPsiFactory.contextual(lambda)
        val bodyExpression = functionLiteral.bodyExpression ?: return null
        var hasNullableReturn = false
        val fileCopy = bodyExpression.containingFile.copied()
        //we don't want to change the code, thus we have to operate on copy
        //bodyExpression.copied() produces expression with JavaDummyHolder containing file,
        //thus we copy the whole file to ensure AA works with it
        val bodyExpressionCopy = PsiTreeUtil.findSameElementInCopy(bodyExpression, fileCopy)
        val functionLiteralInCopy = PsiTreeUtil.findSameElementInCopy(functionLiteral, fileCopy)

        analyze(bodyExpressionCopy) {
            bodyExpressionCopy.collectDescendantsOfType<KtReturnExpression>().forEach {
                val targetSymbol = it.resolveSymbol()
                if (targetSymbol?.psi == functionLiteralInCopy) {
                    val returnType = it.returnedExpression?.expressionType
                    if (returnType?.isNullable == true) {
                        hasNullableReturn = true
                    }
                    it.labeledExpression?.delete()
                }
            }
        }

        return analyze(functionLiteral) {
            val functionSymbol = functionLiteral.symbol
            KtPsiFactory.CallableBuilder(KtPsiFactory.CallableBuilder.Target.FUNCTION).apply {
                typeParams()
                functionSymbol.receiverType?.let {
                    receiver(it.render(position = Variance.INVARIANT))
                }

                name(functionName)

                val nameValidator = CollectingNameValidator(
                    functionSymbol.valueParameters
                        .map { it.name }
                        .filter { !it.isSpecial }
                        .map { it.asString() }
                )

                for ((index, parameter) in functionSymbol.valueParameters.withIndex()) {
                    val parameterType = parameter.returnType
                    val renderType = parameterType.render(position = Variance.IN_VARIANCE)
                    val parameterName = parameter.name

                    val nameToUse = when {
                        // An underscore is suitable only for anonymous functions
                        parameterName.isSpecial && functionName.isEmpty() -> "_"

                        parameterName.isSpecial -> {
                            val suggestedName = parameterNames.getOrNull(index) ?: parameterName.asString()
                            KotlinNameSuggester.suggestNameByName(suggestedName, nameValidator)
                        }

                        else -> parameterName.asString()
                    }

                    param(nameToUse.quoteIfNeeded(), renderType)
                }

                functionSymbol.returnType.takeIf { !it.isUnitType && it !is KaErrorType }?.let {
                    val lastStatement = bodyExpressionCopy.statements.lastOrNull()
                    if (lastStatement != null && lastStatement !is KtReturnExpression) {
                        analyze(lastStatement) {
                            val foldableReturns = BranchedFoldingUtils.getFoldableReturns(lastStatement)
                            if (foldableReturns.isNullOrEmpty()) {
                                if (lastStatement.expressionType?.isNullable == true) {
                                    hasNullableReturn = true
                                }
                                lastStatement.replace(psiFactory.createExpressionByPattern("return $0", lastStatement))
                            }
                        }
                    }
                    val finalReturnType = when {
                        forceNonNullReturnType && !hasNullableReturn -> it.withNullability(false)
                        else -> it
                    }
                    returnType(finalReturnType.render(position = Variance.OUT_VARIANCE))
                } ?: noReturnType()
                blockBody(" " + bodyExpressionCopy.text)
            }.asString()
        }
    }

    fun convertLambdaToFunction(
        lambda: KtLambdaExpression,
        functionText: String
    ): KtExpression {
        val psiFactory = KtPsiFactory.contextual(lambda)
        val function = psiFactory.createFunction(functionText)

        val result = wrapInParenthesisForCallExpression(lambda.replaced(function), psiFactory)

        shortenReferences(result)

        return result
    }

    private fun wrapInParenthesisForCallExpression(expression: KtExpression, psiFactory: KtPsiFactory): KtExpression {
        val parent = expression.parent ?: return expression
        val grandParent = parent.parent ?: return expression

        if (parent is KtCallExpression && grandParent !is KtParenthesizedExpression && grandParent !is KtDeclaration) {
            return expression.replaced(psiFactory.createExpressionByPattern("($0)", expression))
        }

        return expression
    }
}