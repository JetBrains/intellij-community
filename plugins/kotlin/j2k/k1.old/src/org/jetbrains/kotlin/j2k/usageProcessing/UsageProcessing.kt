// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeConverter
import org.jetbrains.kotlin.j2k.SpecialExpressionConverter
import org.jetbrains.kotlin.j2k.ast.Expression

@K1Deprecation
interface UsageProcessing {
    val targetElement: PsiElement
    val convertedCodeProcessor: ConvertedCodeProcessor?
    val javaCodeProcessors: List<ExternalCodeProcessor>
    val kotlinCodeProcessors: List<ExternalCodeProcessor>
}

@K1Deprecation
interface ConvertedCodeProcessor {
    fun convertVariableUsage(expression: PsiReferenceExpression, codeConverter: CodeConverter): Expression? = null

    fun convertMethodUsage(methodCall: PsiMethodCallExpression, codeConverter: CodeConverter): Expression? = null
}

@K1Deprecation
interface ExternalCodeProcessor {
    fun processUsage(reference: PsiReference): Array<PsiReference>?
}

@K1Deprecation
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
