// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_FUNCTION_LITERAL_ARGUMENT_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.clearUserData
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.unpackFunctionLiteral
import java.util.ArrayList
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.last

abstract class AbstractInlinePostProcessor {

    protected abstract fun canMoveLambdaOutsideParentheses(expr: KtCallExpression): Boolean
    protected abstract fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun shortenReferences(pointers: List<SmartPsiElementPointer<KtElement>>): List<KtElement>

    //no tests fail
    protected open fun introduceNamedArguments(pointer: SmartPsiElementPointer<KtElement>) {}
    protected open fun dropArgumentsForDefaultValues(pointer: SmartPsiElementPointer<KtElement>) {}

    fun postProcessInsertedCode(
      pointers: List<SmartPsiElementPointer<KtElement>>,
      commentSaver: CommentSaver
    ): PsiChildRange {
        for (pointer in pointers) {
            restoreComments(pointer)

            introduceNamedArguments(pointer)

            restoreFunctionLiteralArguments(pointer)

            //TODO: do this earlier
            dropArgumentsForDefaultValues(pointer)

            removeRedundantLambdasAndAnonymousFunctions(pointer)

            simplifySpreadArrayOfArguments(pointer)

            removeExplicitTypeArguments(pointer)

            removeRedundantUnitExpressions(pointer)
        }

        val newElements = shortenReferences(pointers)

        for (element in newElements) {
            // clean up user data
            element.forEachDescendantOfType<KtElement> {
                clearUserData(it)
            }
        }

        val childRange = if (newElements.isEmpty()) PsiChildRange.EMPTY else PsiChildRange(newElements.first(), newElements.last())
        if (!childRange.isEmpty) {
            commentSaver.restore(childRange)
        }
        return childRange
    }

    private fun restoreFunctionLiteralArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val expression = pointer.element ?: return
        val callExpressions = ArrayList<KtCallExpression>()

        expression.forEachDescendantOfType<KtExpression>(fun(expr) {
            if (expr.getCopyableUserData(WAS_FUNCTION_LITERAL_ARGUMENT_KEY) == null) return
            assert(expr.unpackFunctionLiteral() != null)

            val argument = expr.parent as? KtValueArgument ?: return
            if (argument is KtLambdaArgument) return
            val argumentList = argument.parent as? KtValueArgumentList ?: return
            if (argument != argumentList.arguments.last()) return
            val callExpression = argumentList.parent as? KtCallExpression ?: return
            if (callExpression.lambdaArguments.isNotEmpty()) return

            //todo callExpression.resolveToCall() ?: return
            callExpressions.add(callExpression)
        })

        callExpressions.forEach {
            if (canMoveLambdaOutsideParentheses(it)) {
                it.moveFunctionLiteralOutsideParentheses()
            }
        }
    }

    private fun restoreComments(pointer: SmartPsiElementPointer<KtElement>) {
        pointer.element?.forEachDescendantOfType<KtExpression> {
            it.getCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY)?.restoreComments(it.parent as? KtDotQualifiedExpression ?: it)
        }
    }
}