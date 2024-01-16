// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.*
import org.jetbrains.kotlin.j2k.CodeConverter
import org.jetbrains.kotlin.j2k.SpecialExpressionConverter
import org.jetbrains.kotlin.j2k.ast.Expression

interface UsageProcessing {
    val targetElement: PsiElement
    val convertedCodeProcessor: ConvertedCodeProcessor?
    val javaCodeProcessors: List<ExternalCodeProcessor>
    val kotlinCodeProcessors: List<ExternalCodeProcessor>
}

interface ConvertedCodeProcessor {
    fun convertVariableUsage(expression: PsiReferenceExpression, codeConverter: CodeConverter): Expression? = null

    fun convertMethodUsage(methodCall: PsiMethodCallExpression, codeConverter: CodeConverter): Expression? = null
}

interface ExternalCodeProcessor {
    fun processUsage(reference: PsiReference): Array<PsiReference>?
}

class UsageProcessingExpressionConverter(val processings: Map<PsiElement, Collection<UsageProcessing>>) : SpecialExpressionConverter {
    override fun convertExpression(expression: PsiExpression, codeConverter: CodeConverter): Expression? {
        if (processings.isEmpty()) return null

        when (expression) {
            is PsiReferenceExpression -> {
                val target = expression.resolve() as? PsiVariable ?: return null
                val forTarget = processings[target] ?: return null
                for (processing in forTarget) {
                    val converted = processing.convertedCodeProcessor?.convertVariableUsage(expression, codeConverter)
                    if (converted != null) return converted
                }
                return null
            }

            is PsiMethodCallExpression -> {
                val target = expression.methodExpression.resolve() as? PsiMethod ?: return null
                val forTarget = processings[target] ?: return null
                for (processing in forTarget) {
                    val converted = processing.convertedCodeProcessor?.convertMethodUsage(expression, codeConverter)
                    if (converted != null) return converted
                }
                return null
            }

            else -> return null
        }
    }
}
