// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

private val COROUTINE_CONTEXT_FQ_NAME =
    StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("coroutineContext"))

internal class KotlinSuspendFunctionWrapper(
  private val executionContext: ExecutionContext,
  private val psiContext: PsiElement?,
  isCoroutineScopeAvailable: Boolean
) : KotlinExpressionWrapper {
    private val coroutineContextKeyword =
        if (isCoroutineScopeAvailable)
            COROUTINE_CONTEXT_FQ_NAME.shortName().asString()
        else
            COROUTINE_CONTEXT_FQ_NAME.asString()

    override fun createWrappedExpressionText(expressionText: String): String {
        checkIfKotlinxCoroutinesIsAvailable()
        return wrapInRunBlocking(expressionText, psiContext?.let { isCoroutineContextAvailable(it) } ?: false)
    }

    @RequiresReadLock
    override fun isApplicable(expression: KtExpression): Boolean {
        return analyze(expression) {
            var result = false
            expression.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (result) return
                    result = isSuspendCall(element) && !isCoroutineContextAvailable(element)
                    super.visitElement(element)
                }
            })
            result
        }
    }

    private fun isSuspendCall(element: PsiElement): Boolean {
        var result = false
        KotlinCallProcessor.process(element) { target ->
            if (target.symbol.let { it is KaNamedFunctionSymbol && it.isSuspend }) {
                result = true
            }
        }
        return result
    }

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

    private fun isCoroutineContextAvailable(from: PsiElement): Boolean {
        return analyze(from.parentOfType<KtElement>(withSelf = true) ?: return false) {
            from.parentsOfType<KtNamedFunction>().any {
                (it.symbol as? KaNamedFunctionSymbol)?.isSuspend ?: false
            } || from.parentsOfType<KtLambdaExpression>().any {
              (it.expressionType as? KaFunctionType)?.isSuspend ?: false
            }
        }
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
