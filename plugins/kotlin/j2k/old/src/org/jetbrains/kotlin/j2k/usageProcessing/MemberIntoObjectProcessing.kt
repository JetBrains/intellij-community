// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.*

class MemberIntoObjectProcessing(private val member: PsiMember, private val objectName: String) : UsageProcessing {
    override val targetElement: PsiElement get() = member

    override val convertedCodeProcessor: ConvertedCodeProcessor? get() = null

    override val javaCodeProcessors = listOf(AppendObjectNameProcessor())

    override val kotlinCodeProcessors = emptyList<ExternalCodeProcessor>()

    inner class AppendObjectNameProcessor : ExternalCodeProcessor {
        override fun processUsage(reference: PsiReference): Array<PsiReference>? {
            val refExpr = reference.element as? PsiReferenceExpression ?: return null
            val qualifier = refExpr.qualifierExpression
            val factory = PsiElementFactory.getInstance(member.project)
            return if (qualifier != null) {
                val newQualifier = factory.createExpressionFromText(qualifier.text + "." + objectName, null)
                qualifier.replace(newQualifier)
                arrayOf(reference)
            }
            else {
                var qualifiedExpr = factory.createExpressionFromText(objectName + "." + refExpr.text, null) as PsiReferenceExpression
                qualifiedExpr = refExpr.replace(qualifiedExpr) as PsiReferenceExpression
                arrayOf(qualifiedExpr)
            }
        }
    }
}