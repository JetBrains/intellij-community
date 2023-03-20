// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.*
import org.jetbrains.kotlin.load.java.JvmAbi

class ToObjectWithOnlyMethodsProcessing(private val psiClass: PsiClass) : UsageProcessing {
    override val targetElement: PsiElement get() = psiClass

    override val convertedCodeProcessor: ConvertedCodeProcessor? get() = null

    override val javaCodeProcessors = listOf(ToObjectWithOnlyMethodsProcessor())

    override val kotlinCodeProcessors = emptyList<ExternalCodeProcessor>()

    inner class ToObjectWithOnlyMethodsProcessor: ExternalCodeProcessor {
        override fun processUsage(reference: PsiReference): Array<PsiReference>? {
            val refExpr = reference.element as? PsiReferenceExpression ?: return null
            val factory = PsiElementFactory.getInstance(psiClass.project)
            var qualifiedExpr = factory.createExpressionFromText(refExpr.text + "." + JvmAbi.INSTANCE_FIELD, null) as PsiReferenceExpression
            qualifiedExpr = refExpr.replace(qualifiedExpr) as PsiReferenceExpression
            return arrayOf(qualifiedExpr)
        }
    }
}