// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeInliner
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

private val LOG = Logger.getInstance(CallableUsageReplacementStrategy::class.java)

class CallableUsageReplacementStrategy(
    private val replacement: CodeToInline,
    private val inlineSetter: Boolean = false
) : UsageReplacementStrategy {
    override fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        if (!AbstractCodeInliner.canBeReplaced(usage)) return null

        return when {
            usage is KtArrayAccessExpression || usage is KtCallExpression -> {
                {
                    val nameExpression = OperatorToFunctionConverter.convert(usage).second
                    createReplacer(nameExpression)?.invoke()
                }
            }

            usage is KtOperationReferenceExpression && usage.getReferencedNameElementType() != KtTokens.IDENTIFIER -> {
                {
                    val nameExpression = OperatorToFunctionConverter.convert(usage.parent as KtExpression).second
                    createReplacer(nameExpression)?.invoke()
                }
            }

            usage is KtSimpleNameExpression -> {
                {
                    CodeInliner(usage, usage, inlineSetter, replacement).doInline()
                }
            }

            else -> {
                val exceptionWithAttachments = KotlinExceptionWithAttachments("Unsupported usage")
                    .withAttachment("type", usage)
                    .withPsiAttachment("usage.kt", usage)

                LOG.error(exceptionWithAttachments)
                null
            }
        }
    }
}