// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.calls.checkers.COROUTINE_CONTEXT_FQ_NAME
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal class KotlinSuspendFunctionWrapper(
    val bindingContext: BindingContext,
    val executionContext: ExecutionContext,
    val psiContext: PsiElement?,
    isCoroutineScopeAvailable: Boolean
) : KotlinExpressionWrapper {
    private val coroutineContextKeyword =
        if (isCoroutineScopeAvailable)
            COROUTINE_CONTEXT_FQ_NAME.shortName().asString()
        else
            COROUTINE_CONTEXT_FQ_NAME.asString()

    override fun createWrappedExpressionText(expressionText: String): String {
        checkIfKotlinxCoroutinesIsAvailable()
        val isCoroutineContextAvailable = runReadAction {
            psiContext.isCoroutineContextAvailable()
        }

        return wrapInRunBlocking(expressionText, isCoroutineContextAvailable)
    }

    override fun isApplicable(expression: KtExpression) = expression.containsSuspendFunctionCall(bindingContext)

    private fun wrapInRunBlocking(expressionText: String, isCoroutineContextAvailable: Boolean) =
        buildString {
            append("kotlinx.coroutines.runBlocking")
            if (isCoroutineContextAvailable) {
                append("""
                     (
                        @kotlin.OptIn(kotlin.ExperimentalStdlibApi::class)
                        $coroutineContextKeyword.minusKey(kotlinx.coroutines.CoroutineDispatcher)
                    )
                """.trimIndent())
            }
            append(" {\n\t")
            append(expressionText)
            append("\n}")
        }

    private fun KtExpression.containsSuspendFunctionCall(bindingContext: BindingContext): Boolean {
        var result = false
        invokeAndWaitIfNeeded {
            accept(object : KtTreeVisitorVoid() {
                override fun visitCallExpression(callExpression: KtCallExpression) {
                    val resolvedCall = callExpression.getResolvedCall(bindingContext)
                    if (resolvedCall != null && resolvedCall.resultingDescriptor.isSuspend &&
                        !callExpression.parentsOfType<KtLambdaExpression>().isCoroutineContextAvailableFromLambda(bindingContext)) {
                        result = true
                        return
                    }
                    callExpression.acceptChildren(this)
                }
            })
        }
        return result
    }

    private fun PsiElement?.isCoroutineContextAvailable() =
        if (this == null) {
            false
        } else {
            parentsOfType<KtNamedFunction>().isCoroutineContextAvailableFromFunction() ||
            parentsOfType<KtLambdaExpression>().isCoroutineContextAvailableFromLambda()
        }

    private fun Sequence<KtNamedFunction>.isCoroutineContextAvailableFromFunction(): Boolean {
        for (item in this) {
            val descriptor = item.descriptor as? CallableDescriptor ?: continue
            if (descriptor.isSuspend) {
                return true
            }
        }

        return false
    }

    private fun Sequence<KtLambdaExpression>.isCoroutineContextAvailableFromLambda(
        bindingContext: BindingContext
    ): Boolean {
        for (item in this) {
            val type = item.getType(bindingContext) ?: continue
            if (type.isSuspendFunctionType) {
                return true
            }
        }

        return false
    }

    private fun Sequence<KtLambdaExpression>.isCoroutineContextAvailableFromLambda() =
        if (none()) {
            false
        } else {
            val bindingContext = last().analyze(BodyResolveMode.PARTIAL)
            isCoroutineContextAvailableFromLambda(bindingContext)
        }

    private fun checkIfKotlinxCoroutinesIsAvailable() {
        val debugProcess = executionContext.debugProcess
        val evaluationContext = executionContext.evaluationContext
        try {
            debugProcess.findClass(evaluationContext, "kotlinx.coroutines.BuildersKt", evaluationContext.classLoader)
        } catch (ex: EvaluateException) {
            evaluationException(KotlinDebuggerEvaluationBundle.message("error.failed.to.wrap.suspend.function"))
        }
    }
}
