// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtPsiFactory

internal interface KotlinCodeFragmentWrapper {
    fun transformIfNeeded(codeFragment: KtCodeFragment)
}

internal class KotlinValueClassToStringWrapper(val stats: CodeFragmentCompilationStats) : KotlinCodeFragmentWrapper {

    companion object {
        private val LOG = Logger.getInstance(KotlinValueClassToStringWrapper::class.java)
    }

    override fun transformIfNeeded(codeFragment: KtCodeFragment) {
        val (expression, expressionText) = extractExpressionWithText(codeFragment) ?: return

        val needsWrapping = stats.startAndMeasureAnalysisUnderReadAction {
            analyze(expression) {
                val ktUsualClassType = expression.expressionType as? KaUsualClassType
                val ktNamedClassOrObjectSymbol = ktUsualClassType?.symbol as? KaNamedClassSymbol
                ktNamedClassOrObjectSymbol?.isInline == true
            }
        }.getOrNull()

        if (needsWrapping != true) return

        replaceExpression(expression, "($expressionText) as Any?")

        return
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

    private fun extractExpressionWithText(codeFragment: KtCodeFragment): Pair<KtExpression, String>? =
        runReadAction {
            val expression = when (codeFragment) {
                is KtExpressionCodeFragment -> codeFragment.getContentElement()
                is KtBlockCodeFragment -> codeFragment.getContentElement().statements.lastOrNull()
                else -> {
                    LOG.error("Invalid code fragment type: ${codeFragment.javaClass}")
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

