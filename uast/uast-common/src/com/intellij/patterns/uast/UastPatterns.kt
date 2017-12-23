/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("UastPatterns")

package com.intellij.patterns.uast

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ObjectPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.*

fun literalExpression() = ULiteralExpressionPattern()

class ULiteralExpressionPattern : ObjectPattern<ULiteralExpression, ULiteralExpressionPattern>(ULiteralExpression::class.java) {

  fun withSourcePsiCondition(pattern: PatternCondition<PsiElement>) =
    this.with(object : PatternCondition<ULiteralExpression?>("withSourcePsiPattern") {
      override fun accepts(t: ULiteralExpression, context: ProcessingContext?): Boolean {
        val sourcePsiElement = t.sourcePsiElement ?: return false
        return pattern.accepts(sourcePsiElement, context)
      }
    })

  fun annotationParam(annotationQualifiedName: ElementPattern<String>, @NonNls parameterName: String) =
    this.with(object : PatternCondition<ULiteralExpression>("annotationParam") {
      override fun accepts(uElement: ULiteralExpression, context: ProcessingContext?): Boolean {
        val namedExpression = uElement.getParentOfType<UNamedExpression>(true) ?: return false
        if ((namedExpression.name ?: "value") != parameterName) return false
        val annotation = namedExpression.getParentOfType<UAnnotation>(true) ?: return false
        return (annotationQualifiedName.accepts(annotation.qualifiedName, context))
      }
    })

  fun annotationParam(@NonNls annotationQualifiedName: String, @NonNls parameterName: String) =
    annotationParam(StandardPatterns.string().equalTo(annotationQualifiedName), parameterName)

  fun filter(filter: (ULiteralExpression) -> Boolean) =
    with(object : PatternCondition<ULiteralExpression>(null) {
      override fun accepts(t: ULiteralExpression, context: ProcessingContext?): Boolean = filter.invoke(t)
    })

}
