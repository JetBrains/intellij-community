// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.BuilderByPattern
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Allows to convert implicit operators' calls to explicit and vice versa.
 *
 * @see org.jetbrains.kotlin.idea.codeInsight.intentions.shared.OperatorToFunctionIntention
 */
object OperatorToFunctionConverter {
    fun replaceExplicitInvokeCallWithImplicit(qualifiedExpression: KtDotQualifiedExpression): KtExpression? {
        /*
        * `a.b.invoke<>(){}` -> `a.b<>(){}`
        * `a.b<>(){}.invoke<>(){}` -> `a.b<>(){}<>(){}`
        * `b.invoke<>(){}` -> `b<>(){}`
        * `b<>(){}.invoke<>(){}` -> `b<>(){}<>(){}`
        * `invoke<>(){}` -> not applicable
        */

        val callExpression = qualifiedExpression.selectorExpression.safeAs<KtCallExpression>()?.copied() ?: return null
        val calleeExpression = callExpression.calleeExpression as KtNameReferenceExpression
        val receiverExpression = qualifiedExpression.receiverExpression
        val selectorInReceiver = receiverExpression.safeAs<KtDotQualifiedExpression>()?.selectorExpression
        return if (selectorInReceiver is KtNameReferenceExpression) {
            calleeExpression.rawReplace(selectorInReceiver.copied())
            selectorInReceiver.rawReplace(callExpression)
            qualifiedExpression.replaced(receiverExpression)
        } else {
            if ((receiverExpression is KtCallExpression || receiverExpression is KtDotQualifiedExpression) &&
                callExpression.valueArgumentList == null && callExpression.typeArgumentList == null
            ) {
                calleeExpression.replace(receiverExpression)
            } else {
                calleeExpression.rawReplace(receiverExpression)
            }

            qualifiedExpression.replaced(callExpression)
        }
    }

    private fun convertUnary(element: KtUnaryExpression): KtExpression {
        val operatorName = when (element.operationReference.getReferencedNameElementType()) {
            KtTokens.PLUSPLUS, KtTokens.MINUSMINUS -> return convertUnaryWithAssignFix(element)
            KtTokens.PLUS -> OperatorNameConventions.UNARY_PLUS
            KtTokens.MINUS -> OperatorNameConventions.UNARY_MINUS
            KtTokens.EXCL -> OperatorNameConventions.NOT
            else -> return element
        }

        val transformed = KtPsiFactory(element.project)
            .createExpressionByPattern("$0.$1()", element.baseExpression!!, operatorName)

        return element.replace(transformed) as KtExpression
    }

    private fun convertUnaryWithAssignFix(element: KtUnaryExpression): KtExpression {
        val operatorName = when (element.operationReference.getReferencedNameElementType()) {
            KtTokens.PLUSPLUS -> OperatorNameConventions.INC
            KtTokens.MINUSMINUS -> OperatorNameConventions.DEC
            else -> return element
        }

        val transformed = KtPsiFactory(element.project)
            .createExpressionByPattern("$0 = $0.$1()", element.baseExpression!!, operatorName)

        return element.replace(transformed) as KtExpression
    }

    //TODO: don't use creation by plain text
    private fun convertBinary(element: KtBinaryExpression): KtExpression {
        val op = element.operationReference.getReferencedNameElementType()
        val left = element.left!!
        val right = element.right!!

        if (op == KtTokens.EQ) {
            if (left is KtArrayAccessExpression) {
                convertArrayAccess(left)
            }
            return element
        }

        val functionName = getCalledFunctionName(element)?.asString()
        val receiverIsNullable = isOfNullableType(left)

        @NonNls
        val pattern = when (op) {
            KtTokens.PLUS -> "$0.plus($1)"
            KtTokens.MINUS -> "$0.minus($1)"
            KtTokens.MUL -> "$0.times($1)"
            KtTokens.DIV -> "$0.div($1)"
            KtTokens.PERC -> "$0.rem($1)"
            KtTokens.RANGE -> "$0.rangeTo($1)"
            KtTokens.RANGE_UNTIL -> "$0.rangeUntil($1)"
            KtTokens.IN_KEYWORD -> "$1.contains($0)"
            KtTokens.NOT_IN -> "!$1.contains($0)"
            KtTokens.PLUSEQ -> if (functionName == "plusAssign") "$0.plusAssign($1)" else "$0 = $0.plus($1)"
            KtTokens.MINUSEQ -> if (functionName == "minusAssign") "$0.minusAssign($1)" else "$0 = $0.minus($1)"
            KtTokens.MULTEQ -> if (functionName == "timesAssign") "$0.timesAssign($1)" else "$0 = $0.times($1)"
            KtTokens.DIVEQ -> if (functionName == "divAssign") "$0.divAssign($1)" else "$0 = $0.div($1)"
            KtTokens.PERCEQ -> {
                when (functionName) {
                  "remAssign" -> "$0.remAssign($1)"
                  "modAssign" -> "$0.modAssign($1)"
                  else -> "$0 = $0.rem($1)"
                }
            }

            KtTokens.EQEQ -> if (receiverIsNullable != false) "$0?.equals($1) ?: ($1 == null)" else "$0.equals($1)"
            KtTokens.EXCLEQ -> if (receiverIsNullable != false) "!($0?.equals($1) ?: ($1 == null))" else "!$0.equals($1)"
            KtTokens.GT -> "$0.compareTo($1) > 0"
            KtTokens.LT -> "$0.compareTo($1) < 0"
            KtTokens.GTEQ -> "$0.compareTo($1) >= 0"
            KtTokens.LTEQ -> "$0.compareTo($1) <= 0"
            else -> return element
        }

        val transformed = KtPsiFactory(element.project).createExpressionByPattern(pattern, left, right)
        return element.replace(transformed) as KtExpression
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun isOfNullableType(expression: KtExpression): Boolean? = allowAnalysisOnEdt {
        @OptIn(KaAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            analyze(expression) {
                expression.expressionType?.isMarkedNullable
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun getCalledFunctionName(element: KtBinaryExpression): Name? = allowAnalysisOnEdt {
        @OptIn(KaAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            analyze(element) {
                val resolvedCall = element.resolveToCall()?.singleFunctionCallOrNull()
                val targetSymbol = resolvedCall?.partiallyAppliedSymbol?.symbol

                (targetSymbol as? KaNamedSymbol)?.name
            }
        }
    }

    private fun convertArrayAccess(element: KtArrayAccessExpression): KtExpression {
        var expressionToReplace: KtExpression = element
        val transformed = KtPsiFactory(element.project).buildExpression {
            appendExpression(element.arrayExpression)

            appendFixedText(".")

            if (isAssignmentLeftSide(element)) {
                val parent = element.parent
                expressionToReplace = parent as KtBinaryExpression

                appendFixedText("set(")
                appendExpressions(element.indexExpressions)
                appendFixedText(",")
                appendIfVarargType(element, this)
                appendExpression(parent.right)
            } else {
                appendFixedText("get(")
                appendExpressions(element.indexExpressions)
            }

            appendFixedText(")")
        }

        return expressionToReplace.replace(transformed) as KtExpression
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun appendIfVarargType(element: KtArrayAccessExpression, pattern: BuilderByPattern<KtExpression>) {
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(element) {
                    val argumentMapping = element.resolveToCall()?.singleFunctionCallOrNull()?.argumentMapping.orEmpty()
                    if (argumentMapping[element.indexExpressions.firstOrNull()]?.symbol?.isVararg == true) {
                        argumentMapping[(element.parent as KtBinaryExpression).right]?.symbol?.name?.asString()
                            .let { pattern.appendFixedText("$it = ") }
                    }
                }
            }
        }
    }


    private fun isAssignmentLeftSide(element: KtArrayAccessExpression): Boolean {
        val parent = element.parent
        return parent is KtBinaryExpression &&
                parent.operationReference.getReferencedNameElementType() == KtTokens.EQ && element == parent.left
    }

    //TODO: don't use creation by plain text
    private fun convertCall(element: KtCallExpression): KtExpression {
        val callee = element.calleeExpression!!
        val receiver = element.parent?.safeAs<KtQualifiedExpression>()?.receiverExpression
        val isAnonymousFunctionWithReceiver =
            receiver != null && (callee.safeDeparenthesize() as? KtNamedFunction)?.receiverTypeReference != null

        val isReceiverToPassAsParameter =
            ((callee.mainReference?.resolve() as? KtCallableDeclaration)?.typeReference?.typeElement as? KtFunctionType)?.receiverTypeReference != null

        // to skip broken code like Main.({ println("hello")})() which is possible during inline anonymous function.
        // see KotlinInlineAnonymousFunctionProcessor.Companion.findFunction
        // and corresponding test InlineVariableOrProperty.testFunctionalPropertyWithReceiver
        val argumentString = element.valueArgumentList?.text?.removeSurrounding("(", ")") ?: ""

        val argumentsWithReceiverIfNeeded = if (isAnonymousFunctionWithReceiver || isReceiverToPassAsParameter) {
            val receiverText = receiver?.text ?: resolveImplicitReceiverText(element).orEmpty()
            val delimiter = if (receiverText.isNotEmpty() && argumentString.isNotEmpty()) ", " else ""
            receiverText + delimiter + argumentString
        } else {
            argumentString
        }

        val transformation = "${callee.text}.${OperatorNameConventions.INVOKE.asString()}" + "($argumentsWithReceiverIfNeeded)"
        val factory = KtPsiFactory(element.project)
        val transformed = factory.createExpression(transformation)

        deleteValueArgumentList(element.lambdaArguments, transformed, argumentsWithReceiverIfNeeded)

        val parent = element.parent
        val isLambdaWithReceiver = receiver != null && receiver != element && callee.safeDeparenthesize() is KtLambdaExpression

        if (shouldLiftDotQualifier(element, isAnonymousFunctionWithReceiver, isReceiverToPassAsParameter, isLambdaWithReceiver)) {
            val newElement = factory.createExpression((parent as KtDotQualifiedExpression).receiverExpression.text + "." + transformed.text)
            return (parent.replace(newElement) as KtDotQualifiedExpression).selectorExpression as KtExpression
        }

        val elementToReplace =
            if (isAnonymousFunctionWithReceiver || isLambdaWithReceiver || (receiver != null && isReceiverToPassAsParameter)) element.parent else element
        return elementToReplace.replace(transformed) as KtExpression
    }

    /**
     * Converts an operator call to an explicit function call.
     *
     * N.B. Has to use some resolve inside, so resorts to [allowAnalysisOnEdt].
     */
    fun convert(element: KtExpression): Pair<KtExpression, KtSimpleNameExpression> {
        var elementToBeReplaced = element
        if (element is KtArrayAccessExpression && isAssignmentLeftSide(element)) {
            elementToBeReplaced = element.parent as KtExpression
        }

        val commentSaver = CommentSaver(elementToBeReplaced, saveLineBreaks = true)

        val result = when (element) {
            is KtUnaryExpression -> convertUnary(element)
            is KtBinaryExpression -> convertBinary(element)
            is KtArrayAccessExpression -> convertArrayAccess(element)
            is KtCallExpression -> convertCall(element)
            else -> throw IllegalArgumentException(element.toString())
        }

        commentSaver.restore(result)

        val callName = findCallName(result) ?: error("No call name found in ${result.text}")
        return result to callName
    }

    private fun deleteValueArgumentList(
        lambdaArguments: List<KtLambdaArgument>,
        transformed: KtExpression,
        arguments: String
    ) {
        val callExpression = transformed.getCalleeExpressionIfAny()?.parent as? KtCallExpression
        if (callExpression != null && lambdaArguments.isNotEmpty()) {
            lambdaArguments.forEach { callExpression.add(it) }
            if (arguments.isEmpty()) {
                callExpression.valueArgumentList?.delete()
            }
        }
    }

    private fun shouldLiftDotQualifier(
        element: KtCallExpression,
        isAnonymousFunctionWithReceiver: Boolean,
        isReceiverToPassAsParameter: Boolean,
        isLambdaWithReceiver: Boolean
    ): Boolean {
        /* lift dot qualifier (calleeText) to the upper level, otherwise psi is created as
         dotQualified
         - receiver
         - selection dotQualified
           - calleeText
           - callExpression newName(args)
         though from text the following structure is expected
         dotQualified
          - receiver dotQualified
             - receiver
             - calleeText
          - callExpression newName(arge)*/
        val parent = element.parent
        return parent is KtDotQualifiedExpression &&
                parent.selectorExpression == element &&
                !isAnonymousFunctionWithReceiver &&
                !isReceiverToPassAsParameter &&
                !isLambdaWithReceiver
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    private fun resolveImplicitReceiverText(element: KtCallExpression): String? {
        val callee = element.calleeExpression!!
        val owner = allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(callee) {
                    val partiallyAppliedSymbol = element.resolveToCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol
                    val symbol = (partiallyAppliedSymbol?.extensionReceiver as? KaImplicitReceiverValue)?.symbol
                    (symbol as? KaReceiverParameterSymbol)?.owningCallableSymbol?.psi
                }
            }
        }
        return when (owner) {
            is KtFunctionLiteral -> {
                val label = owner.findLabelAndCall().first?.asString()
                "this" + (if (label != null) "@$label" else "")
            }
            is KtNamedDeclaration -> owner.name?.let { "this@$it" }
            else -> null
        }
    }

    private fun findCallName(result: KtExpression): KtSimpleNameExpression? = when (result) {
        is KtBinaryExpression -> {
            if (KtPsiUtil.isAssignment(result))
                findCallName(result.right!!)
            else
                findCallName(result.left!!)
        }

        is KtUnaryExpression -> result.baseExpression?.let { findCallName(it) }
        is KtParenthesizedExpression -> result.expression?.let { findCallName(it) }
        else -> result.getQualifiedElementSelector() as KtSimpleNameExpression?
    }
}
