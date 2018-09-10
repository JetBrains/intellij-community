/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("UastPatterns")

package com.intellij.patterns.uast

import com.intellij.patterns.*
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.*

fun literalExpression(): ULiteralExpressionPattern = ULiteralExpressionPattern()

fun stringLiteralExpression(): ULiteralExpressionPattern = literalExpression().filter(ULiteralExpression::isStringLiteral)

fun callExpression(): UCallExpressionPattern = UCallExpressionPattern()

fun <T : UElement> capture(clazz: Class<T>): UElementPattern.Capture<T> = UElementPattern.Capture(clazz)

open class UElementPattern<T : UElement, Self : UElementPattern<T, Self>>(clazz: Class<T>) : ObjectPattern<T, Self>(clazz) {
  fun withSourcePsiCondition(pattern: PatternCondition<PsiElement>): Self =
    this.with(object : PatternCondition<T>("withSourcePsiPattern") {
      override fun accepts(t: T, context: ProcessingContext?): Boolean {
        val sourcePsiElement = t.sourcePsiElement ?: return false
        return pattern.accepts(sourcePsiElement, context)
      }
    })

  fun sourcePsiFilter(filter: (PsiElement) -> Boolean): Self =
    withSourcePsiCondition(object : PatternCondition<PsiElement>("sourcePsiFilter") {
      override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean = filter(t)
    })

  fun filter(filter: (T) -> Boolean): Self =
    with(object : PatternCondition<T>(null) {
      override fun accepts(t: T, context: ProcessingContext?): Boolean = filter.invoke(t)
    })

  fun inCall(callPattern: ElementPattern<UCallExpression>): Self =
    filter { it.getUCallExpression()?.let { callPattern.accepts(it) } ?: false }

  fun callParameter(parameterIndex: Int, callPattern: ElementPattern<UCallExpression>): Self =
    filter {
      val call = it.getUCallExpression() as? UCallExpressionEx ?: return@filter false
      call.getArgumentForParameter(parameterIndex) == it && callPattern.accepts(call)
    }

  fun constructorParameter(parameterIndex: Int, classFQN: String): Self = callParameter(parameterIndex, callExpression().constructor(classFQN))

  class Capture<T : UElement>(clazz: Class<T>) : UElementPattern<T, Capture<T>>(clazz)
}

class UCallExpressionPattern : UElementPattern<UCallExpression, UCallExpressionPattern>(UCallExpression::class.java) {

  fun withReceiver(classPattern: ElementPattern<PsiClass>): UCallExpressionPattern =
    filter { (it.receiverType as? PsiClassType)?.resolve()?.let { classPattern.accepts(it) } ?: false }

  fun withMethodName(methodName : String): UCallExpressionPattern = withMethodName(string().equalTo(methodName))

  fun withMethodName(namePattern: ElementPattern<String>): UCallExpressionPattern = filter { it.methodName?.let { namePattern.accepts(it) } ?: false }

  fun constructor(classPattern: ElementPattern<PsiClass>): UCallExpressionPattern = filter {
    val psiMethod = it.resolve() ?: return@filter false;
    psiMethod.isConstructor && classPattern.accepts(psiMethod.containingClass)
  }

  fun constructor(className: String): UCallExpressionPattern = constructor(PsiJavaPatterns.psiClass().withQualifiedName(className))

}

class ULiteralExpressionPattern : UElementPattern<ULiteralExpression, ULiteralExpressionPattern>(ULiteralExpression::class.java) {

  fun annotationParam(@NonNls parameterName: String, annotationPattern: ElementPattern<UAnnotation>): ULiteralExpressionPattern =
    this.with(object : PatternCondition<ULiteralExpression>("annotationParam") {
      override fun accepts(uElement: ULiteralExpression, context: ProcessingContext?): Boolean {
        val namedExpression = uElement.getParentOfType<UNamedExpression>(true) ?: return false
        if ((namedExpression.name ?: "value") != parameterName) return false
        val annotation = namedExpression.getParentOfType<UAnnotation>(true) ?: return false
        return (annotationPattern.accepts(annotation, context))
      }
    })

  fun annotationParam(annotationQualifiedName: ElementPattern<String>, @NonNls parameterName: String): ULiteralExpressionPattern =
    annotationParam(parameterName, capture(UAnnotation::class.java)
      .filter { it.qualifiedName?.let { annotationQualifiedName.accepts(it) } ?: false })

  fun annotationParam(@NonNls annotationQualifiedName: String, @NonNls parameterName: String): ULiteralExpressionPattern =
    annotationParam(StandardPatterns.string().equalTo(annotationQualifiedName), parameterName)

}
