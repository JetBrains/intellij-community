// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*

private class KotlinCodeFragmentPatcher(val codeFragment: KtCodeFragment) {
    companion object {
        private val LOG = Logger.getInstance(KotlinCodeFragmentPatcher::class.java)
    }

    private val expressionWrappers = mutableListOf<KotlinExpressionWrapper>()

    fun addWrapper(wrapper: KotlinExpressionWrapper): KotlinCodeFragmentPatcher {
        expressionWrappers.add(wrapper)
        return this
    }

    fun wrapFragmentExpressionIfNeeded(stats: CodeFragmentCompilationStats) {
        val (expression, expressionText) = extractExpressionWithText() ?: return

        val newExpressionText = stats.startAndMeasureWrapAnalysisUnderReadAction {
            var newExpressionText = expressionText
            for (wrapper in expressionWrappers) {
                if (wrapper.isApplicable(expression)) {
                    newExpressionText = wrapper.createWrappedExpressionText(newExpressionText)
                }
            }
            newExpressionText
        }.getOrThrow()

        if (newExpressionText != expressionText) {
            replaceExpression(expression, newExpressionText)
        }
    }

    private fun replaceExpression(expression: KtExpression, newExpressionText: String) {
        val newExpression = runReadAction {
            KtPsiFactory(expression.project).createExpression(newExpressionText)
        }

        invokeAndWaitIfNeeded {
            expression.project.executeWriteCommand(
                KotlinDebuggerEvaluationBundle.message("wrap.expression")
            ) {
                expression.replace(newExpression)
            }
        }
    }

    private fun extractExpressionWithText(): Pair<KtExpression, String>? =
        runReadAction {
            val expression = when (codeFragment) {
                is KtExpressionCodeFragment -> codeFragment.getContentElement()
                is KtBlockCodeFragment -> codeFragment.getContentElement().statements.lastOrNull()
                else -> {
                    LOG.error("Invalid code fragment type: ${this.javaClass}")
                    null
                }
            }

            if (expression == null) {
                null
            } else {
                Pair(expression, expression.text)
            }
        }
}

internal fun patchCodeFragment(context: ExecutionContext, codeFragment: KtCodeFragment, stats: CodeFragmentCompilationStats) {
    KotlinCodeFragmentPatcher(codeFragment)
        .addWrapper(KotlinValueClassToStringWrapper())
        .addWrapper(
            KotlinSuspendFunctionWrapper(
                context,
                codeFragment.context,
                (context.frameProxy as? CoroutineStackFrameProxyImpl)?.isCoroutineScopeAvailable() ?: false
            )
        ).wrapFragmentExpressionIfNeeded(stats)
}
