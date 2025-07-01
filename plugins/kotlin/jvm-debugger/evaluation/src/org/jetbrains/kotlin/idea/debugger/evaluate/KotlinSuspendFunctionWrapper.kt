// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.CallTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallTargetProcessor
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

private val COROUTINE_CONTEXT_FQ_NAME =
    StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("coroutineContext"))

internal class KotlinSuspendFunctionWrapper(
    private val stats: CodeFragmentCompilationStats,
    private val executionContext: ExecutionContext,
    private val psiContext: PsiElement?,
    isCoroutineScopeAvailable: Boolean
) : KotlinCodeFragmentWrapper {

    private val coroutineContextKeyword =
        if (isCoroutineScopeAvailable)
            COROUTINE_CONTEXT_FQ_NAME.shortName().asString()
        else
            COROUTINE_CONTEXT_FQ_NAME.asString()

    private val KtCodeFragment.needsTransforming: Boolean
        @RequiresReadLock
        get() {
            if (this !is KtBlockCodeFragment && this !is KtExpressionCodeFragment) return false
            return analyze(this) {
                var result = false
                accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (result) return
                        result = isSuspendCall(element) && !isCoroutineContextAvailable(element)
                        super.visitElement(element)
                    }
                })
                result
            }
        }

    @RequiresReadLock
    override fun transformIfNeeded(codeFragment: KtCodeFragment) {
        if (stats.startAndMeasureAnalysisUnderReadAction { codeFragment.needsTransforming }.getOrNull() != true) return

        checkIfKotlinxCoroutinesIsAvailable()

        val wrappedInRunBlocking =
            wrapInRunBlocking(
                codeFragment.text,
                psiContext?.let {
                    runReadAction { isCoroutineContextAvailable(it) }
                } == true)

        val wrappedExpr = runReadAction { KtPsiFactory(codeFragment.project).createExpression(wrappedInRunBlocking) }

        invokeAndWaitIfNeeded {
            codeFragment.project.executeWriteCommand(KotlinDebuggerEvaluationBundle.message("wrap.expression")) {
                when (codeFragment) {
                    is KtBlockCodeFragment -> {
                        codeFragment.getContentElement().removeAllChildren()
                        codeFragment.getContentElement().addChild(wrappedExpr.node)
                    }
                    is KtExpressionCodeFragment -> codeFragment.getContentElement()?.replace(wrappedExpr)
                }
            }
        }
    }

    private fun isSuspendCall(element: PsiElement): Boolean {
        var result = false
        KotlinCallProcessor.process(element, object : KotlinCallTargetProcessor {

            override fun KaSession.processCallTarget(target: CallTarget): Boolean {
                if (target.symbol.let { it is KaNamedFunctionSymbol && it.isSuspend }) {
                    result = true
                }
                return true
            }

            override fun KaSession.processUnresolvedCall(element: KtElement, callInfo: KaCallInfo?): Boolean {
                if (callInfo is KaErrorCallInfo
                    && callInfo.candidateCalls.size == 1
                    && callInfo.diagnostic.factoryName == "INVISIBLE_MEMBER"
                ) {
                    return processResolvedCall(this, element, callInfo.candidateCalls.single())
                }
                return true
            }

            private fun processResolvedCall(session: KaSession, element: KtElement, call: KaCall): Boolean {
                val targetProcessor = this
                return with(KotlinCallProcessor) { session.processResolvedCall(targetProcessor, element, call) }
            }

        })
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

    @RequiresReadLock
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
