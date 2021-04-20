package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.isInlineClassType

internal interface KotlinExpressionWrapper {
    fun createWrappedExpressionText(expressionText: String): String

    fun isApplicable(expression: KtExpression): Boolean
}

internal class KotlinToStringWrapper(val bindingContext: BindingContext) : KotlinExpressionWrapper {
    override fun createWrappedExpressionText(expressionText: String) = "($expressionText).toString()"

    override fun isApplicable(expression: KtExpression): Boolean {
        val expressionType = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, expression]?.type ?: return false
        return expressionType.isInlineClassType()
    }
}
