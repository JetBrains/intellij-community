// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CodeInliner
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.fullyExpandCall
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractKotlinDeclarationInlineProcessor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinInlineAnonymousFunctionProcessor(
    function: KtFunction,
    private val usage: KtExpression,
    editor: Editor?,
    project: Project,
) : AbstractKotlinDeclarationInlineProcessor<KtFunction>(function, editor, project) {
    override fun findUsages(): Array<UsageInfo> = arrayOf(UsageInfo(usage))

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        performRefactoring(usage, editor)
    }

    companion object {
        fun findCallExpression(function: KtFunction): KtExpression? {
            val psiElement = function.parents
                .takeWhile { it is KtParenthesizedExpression || it is KtLambdaExpression }
                .lastOrNull()?.parent as? KtExpression

            return psiElement?.takeIf {
                it is KtCallExpression || it is KtQualifiedExpression && it.selectorExpression.isInvokeCall
            }
        }

        @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class) //under potemkin progress
        fun performRefactoring(usage: KtExpression, editor: Editor?) {
            val project = usage.project
            val invokeCallExpression = when (usage) {
                is KtQualifiedExpression -> usage.selectorExpression
                is KtCallExpression -> OperatorToFunctionConverter.convert(usage).second.parent
                else -> return
            } as KtCallExpression

            invokeCallExpression.calleeExpression?.safeAs<KtReferenceExpression>()?.let {
                fullyExpandCall(it)
            }

            val qualifiedExpression = invokeCallExpression.parent as KtQualifiedExpression
            val functionLiteral = findFunction(qualifiedExpression) ?: return showErrorHint(
                project,
                editor,
                KotlinBundle.message("refactoring.the.function.not.found")
            )

            val function = convertFunctionToAnonymousFunction(functionLiteral) ?: return showErrorHint(
                project,
                editor,
                KotlinBundle.message("refactoring.the.function.cannot.be.converted.to.anonymous.function")
            )

            val codeToInline = createCodeToInlineForFunction(function, editor) ?: return

            allowAnalysisOnEdt {
                allowAnalysisFromWriteAction {
                    CodeInliner(
                        usageExpression = null,
                        call = invokeCallExpression,
                        inlineSetter = false,
                        replacement = codeToInline,
                    ).doInline()
                }
            }
        }

        private fun findFunction(qualifiedExpression: KtQualifiedExpression): KtFunction? =
            when (val expression = qualifiedExpression.receiverExpression.safeDeparenthesize()) {
                is KtLambdaExpression -> expression.functionLiteral
                is KtNamedFunction -> expression
                is KtDotQualifiedExpression -> expression.selectorExpression?.let { expr ->
                    val deparenthesize = expr.safeDeparenthesize()
                    (deparenthesize as? KtLambdaExpression)?.functionLiteral ?: deparenthesize as? KtNamedFunction
                }
                else -> null
            }

        @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
        private fun convertFunctionToAnonymousFunction(function: KtFunction): KtNamedFunction? {
            return when (function) {
                is KtNamedFunction -> function
                is KtFunctionLiteral -> {
                    val lambdaExpression = function.parent as? KtLambdaExpression ?: return null
                    val signature = allowAnalysisOnEdt {
                        allowAnalysisFromWriteAction {
                            analyze(lambdaExpression) {
                                LambdaToAnonymousFunctionUtil.prepareFunctionText(lambdaExpression)
                            }
                        }
                    } ?: return null
                    LambdaToAnonymousFunctionUtil.convertLambdaToFunction(lambdaExpression, signature) as? KtNamedFunction
                }

                else -> null
            }
        }

        private fun showErrorHint(project: Project, editor: Editor?, message: @NlsContexts.DialogMessage String) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                message,
                KotlinBundle.message("title.inline.function"),
                HelpID.INLINE_METHOD
            )
        }
    }
}

private val KtExpression?.isInvokeCall: Boolean
    get() {
        if (this !is KtCallExpression) return false
        val callName = calleeExpression?.text ?: return false
        return callName == OperatorNameConventions.INVOKE.asString()
    }
