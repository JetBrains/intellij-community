// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.load.java.JvmAbi

@K1Deprecation
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