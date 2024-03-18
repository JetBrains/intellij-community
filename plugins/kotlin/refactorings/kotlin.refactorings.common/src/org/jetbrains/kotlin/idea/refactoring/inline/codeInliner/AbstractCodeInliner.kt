// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class AbstractCodeInliner<TCallElement : KtElement>(
    private val callElement: TCallElement,
    codeToInline: CodeToInline
) {
    protected val codeToInline = codeToInline.toMutable()
    protected val project = callElement.project
    protected val psiFactory = KtPsiFactory(project)

    protected fun MutableCodeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced: KtElement) {
        if (elementToBeReplaced !is KtCallableReferenceExpression) return
        val qualified = mainExpression?.safeAs<KtQualifiedExpression>() ?: return
        val reference = qualified.callExpression?.calleeExpression ?: qualified.selectorExpression ?: return
        val callableReference = if (elementToBeReplaced.receiverExpression == null) {
            psiFactory.createExpressionByPattern("::$0", reference)
        } else {
            psiFactory.createExpressionByPattern("$0::$1", qualified.receiverExpression, reference)
        }
        codeToInline.replaceExpression(qualified, callableReference)
    }

    protected fun KtElement.callableReferenceExpressionForReference(): KtCallableReferenceExpression? =
        parent.safeAs<KtCallableReferenceExpression>()?.takeIf { it.callableReference == callElement }

    protected fun KtSimpleNameExpression.receiverExpression(): KtExpression? =
        getReceiverExpression() ?: parent.safeAs<KtCallableReferenceExpression>()?.receiverExpression

    protected fun KtExpression?.shouldKeepValue(usageCount: Int): Boolean {
        if (usageCount == 1) return false
        val sideEffectOnly = usageCount == 0

        return when (this) {
            is KtSimpleNameExpression -> false
            is KtQualifiedExpression -> receiverExpression.shouldKeepValue(usageCount) || selectorExpression.shouldKeepValue(usageCount)
            is KtUnaryExpression -> operationToken in setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) ||
                    baseExpression.shouldKeepValue(usageCount)

            is KtStringTemplateExpression -> entries.any {
                if (sideEffectOnly) it.expression.shouldKeepValue(usageCount) else it is KtStringTemplateEntryWithExpression
            }

            is KtThisExpression, is KtSuperExpression, is KtConstantExpression -> false
            is KtParenthesizedExpression -> expression.shouldKeepValue(usageCount)
            is KtArrayAccessExpression -> !sideEffectOnly ||
                    arrayExpression.shouldKeepValue(usageCount) ||
                    indexExpressions.any { it.shouldKeepValue(usageCount) }

            is KtBinaryExpression -> !sideEffectOnly ||
                    operationToken == KtTokens.IDENTIFIER ||
                    left.shouldKeepValue(usageCount) ||
                    right.shouldKeepValue(usageCount)

            is KtIfExpression -> !sideEffectOnly ||
                    condition.shouldKeepValue(usageCount) ||
                    then.shouldKeepValue(usageCount) ||
                    `else`.shouldKeepValue(usageCount)

            is KtBinaryExpressionWithTypeRHS -> !(sideEffectOnly && left.isNull())
            is KtClassLiteralExpression -> false
            is KtCallableReferenceExpression -> false
            null -> false
            else -> true
        }
    }

    protected fun postProcessInsertedCode(
        pointers: List<SmartPsiElementPointer<KtElement>>
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

        return if (newElements.isEmpty()) PsiChildRange.EMPTY else PsiChildRange(newElements.first(), newElements.last())
    }

    private fun restoreFunctionLiteralArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val expression = pointer.element ?: return
        val callExpressions = ArrayList<KtCallExpression>()

        expression.forEachDescendantOfType<KtExpression>(fun(expr) {
            if (!expr[WAS_FUNCTION_LITERAL_ARGUMENT_KEY]) return
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
            it.getCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY)?.restoreComments(it)
        }
    }

    protected abstract fun canMoveLambdaOutsideParentheses(expr: KtCallExpression): Boolean
    protected abstract fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun introduceNamedArguments(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun dropArgumentsForDefaultValues(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>)
    protected abstract fun shortenReferences(pointers: List<SmartPsiElementPointer<KtElement>>): List<KtElement>

    protected fun findAndMarkNewDeclarations() {
        for (it in codeToInline.statementsBefore) {
            if (it is KtNamedDeclaration) {
                it.mark(NEW_DECLARATION_KEY)
            }
        }
    }

    protected operator fun <T : Any> PsiElement.get(key: Key<T>): T? = getCopyableUserData(key)
    protected operator fun PsiElement.get(key: Key<Unit>): Boolean = getCopyableUserData(key) != null
    protected fun <T : Any> KtElement.clear(key: Key<T>) = putCopyableUserData(key, null)
    protected fun <T : Any> KtElement.put(key: Key<T>, value: T) = putCopyableUserData(key, value)
    protected fun KtElement.mark(key: Key<Unit>) = putCopyableUserData(key, Unit)
    protected fun <T : KtElement> T.marked(key: Key<Unit>): T {
        putCopyableUserData(key, Unit)
        return this
    }

    protected open fun clearUserData(it: KtElement) {
        it.clear(CommentHolder.COMMENTS_TO_RESTORE_KEY)
        it.clear(USER_CODE_KEY)
        it.clear(CodeToInline.PARAMETER_USAGE_KEY)
        it.clear(CodeToInline.TYPE_PARAMETER_USAGE_KEY)
        it.clear(CodeToInline.FAKE_SUPER_CALL_KEY)

        it.clear(RECEIVER_VALUE_KEY)
        it.clear(WAS_FUNCTION_LITERAL_ARGUMENT_KEY)
        it.clear(NEW_DECLARATION_KEY)
        it.clear(MAKE_ARGUMENT_NAMED_KEY)
        it.clear(DEFAULT_PARAMETER_VALUE_KEY)
    }

    companion object {
        // keys below are used on expressions
        @JvmStatic
        protected val USER_CODE_KEY = Key<Unit>("USER_CODE")

        @JvmStatic
        protected val RECEIVER_VALUE_KEY = Key<Unit>("RECEIVER_VALUE")
        @JvmStatic
        protected val WAS_FUNCTION_LITERAL_ARGUMENT_KEY = Key<Unit>("WAS_FUNCTION_LITERAL_ARGUMENT")
        @JvmStatic
        protected val NEW_DECLARATION_KEY = Key<Unit>("NEW_DECLARATION")

        // these keys are used on KtValueArgument
        @JvmStatic
        protected val MAKE_ARGUMENT_NAMED_KEY = Key<Unit>("MAKE_ARGUMENT_NAMED")
        @JvmStatic
        protected val DEFAULT_PARAMETER_VALUE_KEY = Key<Unit>("DEFAULT_PARAMETER_VALUE")

        fun canBeReplaced(element: KtElement): Boolean = when (element) {
            is KtExpression, is KtAnnotationEntry, is KtSuperTypeCallEntry -> true
            else -> false
        }
    }
}