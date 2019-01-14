/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("UastPatterns")

package com.intellij.patterns.uast

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.RecursionManager
import com.intellij.patterns.*
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.*

fun literalExpression(): ULiteralExpressionPattern = ULiteralExpressionPattern()

@Deprecated("Interpolated strings (in Kotlin) are not single string literals, use `injectionHostUExpression()` to be language-abstract",
            ReplaceWith("injectionHostUExpression()", "com.intellij.patterns.uast.injectionHostUExpression"))
fun stringLiteralExpression(): ULiteralExpressionPattern = literalExpression().filter(ULiteralExpression::isStringLiteral)

@JvmOverloads
fun injectionHostUExpression(strict: Boolean = true): UExpressionPattern<UExpression, *> =
  uExpression().filterWithContext { _, processingContext ->
    val requestedPsi = processingContext?.get(REQUESTED_PSI_ELEMENT)
    if (requestedPsi == null) {
      if (strict && ApplicationManager.getApplication().isUnitTestMode) {
        throw AssertionError("no ProcessingContext with `REQUESTED_PSI_ELEMENT` passed for `injectionHostUExpression`," +
                             " please consider creating one using `UastPatterns.withRequestedPsi`, providing a source psi for which " +
                             " this pattern was originally created, or make this `injectionHostUExpression` non-strict.")
      }
      else return@filterWithContext !strict
    }
    return@filterWithContext requestedPsi is PsiLanguageInjectionHost
  }

fun injectionHostOrReferenceExpression(): UExpressionPattern.Capture<UExpression> =
  uExpression().filter { it is UReferenceExpression || it.isInjectionHost() }

fun callExpression(): UCallExpressionPattern = UCallExpressionPattern()

fun uExpression(): UExpressionPattern.Capture<UExpression> = expressionCapture(UExpression::class.java)

fun <T : UElement> capture(clazz: Class<T>): UElementPattern.Capture<T> = UElementPattern.Capture(clazz)

fun <T : UExpression> expressionCapture(clazz: Class<T>): UExpressionPattern.Capture<T> = UExpressionPattern.Capture(clazz)

fun ProcessingContext.withRequestedPsi(psiElement: PsiElement) = this.apply { put(REQUESTED_PSI_ELEMENT, psiElement) }

fun withRequestedPsi(psiElement: PsiElement) = ProcessingContext().withRequestedPsi(psiElement)

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

  fun filterWithContext(filter: (T, ProcessingContext?) -> Boolean): Self =
    with(object : PatternCondition<T>(null) {
      override fun accepts(t: T, context: ProcessingContext?): Boolean = filter.invoke(t, context)
    })

  fun filter(filter: (T) -> Boolean): Self = filterWithContext { t, processingContext -> filter(t) }

  open fun inCall(callPattern: ElementPattern<UCallExpression>): Self =
    throw UnsupportedOperationException("implemented only for UExpressionPatterns")

  open fun callParameter(parameterIndex: Int, callPattern: ElementPattern<UCallExpression>): Self =
    throw UnsupportedOperationException("implemented only for UExpressionPatterns")

  open fun constructorParameter(parameterIndex: Int, classFQN: String): Self = throw UnsupportedOperationException(
    "implemented only for UExpressionPatterns")

  open fun setterParameter(methodPattern: ElementPattern<out PsiMethod>): Self = throw UnsupportedOperationException(
    "implemented only for UExpressionPatterns")

  open fun methodCallParameter(parameterIndex: Int, methodPattern: ElementPattern<out PsiMethod>): Self =
    throw UnsupportedOperationException("implemented only for UExpressionPatterns")

  open fun arrayAccessParameterOf(receiverClassPattern: ElementPattern<PsiClass>): Self = throw UnsupportedOperationException(
    "implemented only for UExpressionPatterns")

  fun withUastParent(parentPattern: ElementPattern<out UElement>): Self = filter { it.uastParent?.let { parentPattern.accepts(it) } ?: false }

  class Capture<T : UElement>(clazz: Class<T>) : UElementPattern<T, Capture<T>>(clazz)
}

private val constructorOrMethodCall = setOf(UastCallKind.CONSTRUCTOR_CALL, UastCallKind.METHOD_CALL)

private fun isCallExpressionParameter(argumentExpression: UExpression,
                                      parameterIndex: Int,
                                      callPattern: ElementPattern<UCallExpression>): Boolean {
  val call = argumentExpression.uastParent.getUCallExpression(searchLimit = 2) as? UCallExpressionEx ?: return false
  if (call.kind !in constructorOrMethodCall) return false
  return call.getArgumentForParameter(parameterIndex) == unwrapPolyadic(argumentExpression) && callPattern.accepts(call)
}

private val GUARD = RecursionManager.createGuard("isPropertyAssignCall")

private fun isPropertyAssignCall(argument: UElement, methodPattern: ElementPattern<out PsiMethod>): Boolean {
  val uBinaryExpression = (argument.uastParent as? UBinaryExpression) ?: return false
  if (uBinaryExpression.operator != UastBinaryOperator.ASSIGN) return false

  val leftOperand = uBinaryExpression.leftOperand

  val uastReference = when (leftOperand) {
    is UQualifiedReferenceExpression -> leftOperand.selector
    is UReferenceExpression -> leftOperand
    else -> return false
  }
  val references = GUARD.doPreventingRecursion(argument, false) {
    uastReference.sourcePsi?.references // via `sourcePsi` because of KT-27385
  } ?: return false
  return references.any { methodPattern.accepts(it.resolve()) }
}

class UCallExpressionPattern : UElementPattern<UCallExpression, UCallExpressionPattern>(UCallExpression::class.java) {

  fun withReceiver(classPattern: ElementPattern<PsiClass>): UCallExpressionPattern =
    filter { (it.receiverType as? PsiClassType)?.resolve()?.let { classPattern.accepts(it) } ?: false }

  fun withMethodName(methodName : String): UCallExpressionPattern = withMethodName(string().equalTo(methodName))

  fun withAnyResolvedMethod(method: ElementPattern<out PsiMethod>): UCallExpressionPattern = withResolvedMethod(method, true)

  fun withResolvedMethod(method: ElementPattern<out PsiMethod>, multiResolve: Boolean): UCallExpressionPattern = filter { uCallExpression ->
    if (multiResolve && uCallExpression is UMultiResolvable)
      uCallExpression.multiResolve().any { method.accepts(it.element) }
    else
      uCallExpression.resolve().let { method.accepts(it) }
  }

  fun withMethodName(namePattern: ElementPattern<String>): UCallExpressionPattern = filter { it.methodName?.let { namePattern.accepts(it) } ?: false }

  fun constructor(classPattern: ElementPattern<PsiClass>): UCallExpressionPattern = filter {
    val psiMethod = it.resolve() ?: return@filter false;
    psiMethod.isConstructor && classPattern.accepts(psiMethod.containingClass)
  }

  fun constructor(className: String): UCallExpressionPattern = constructor(PsiJavaPatterns.psiClass().withQualifiedName(className))

}

open class UExpressionPattern<T : UExpression, Self : UExpressionPattern<T, Self>>(clazz: Class<T>) : UElementPattern<T, Self>(clazz) {

  fun annotationParam(@NonNls parameterName: String, annotationPattern: ElementPattern<UAnnotation>): Self =
    annotationParams(annotationPattern, StandardPatterns.string().equalTo(parameterName))

  fun annotationParams(annotationPattern: ElementPattern<UAnnotation>, parameterNames: ElementPattern<String>): Self =
    this.with(object : PatternCondition<T>("annotationParam") {

      override fun accepts(uElement: T, context: ProcessingContext?): Boolean {
        val (annotation, paramName) = getContainingUAnnotationEntry(uElement) ?: return false
        return parameterNames.accepts(paramName ?: "value") && annotationPattern.accepts(annotation, context)
      }
    })

  fun annotationParam(annotationQualifiedName: ElementPattern<String>, @NonNls parameterName: String): Self =
    annotationParam(parameterName, qualifiedNamePattern(annotationQualifiedName))

  private fun qualifiedNamePattern(annotationQualifiedName: ElementPattern<String>): UElementPattern<UAnnotation, *> =
    capture(UAnnotation::class.java).filter { it.qualifiedName?.let { annotationQualifiedName.accepts(it) } ?: false }

  fun annotationParam(@NonNls annotationQualifiedName: String, @NonNls parameterName: String): Self =
    annotationParam(StandardPatterns.string().equalTo(annotationQualifiedName), parameterName)

  fun annotationParams(@NonNls annotationQualifiedName: String, @NonNls parameterNames: ElementPattern<String>): Self =
    annotationParams(qualifiedNamePattern(StandardPatterns.string().equalTo(annotationQualifiedName)), parameterNames)

  override fun inCall(callPattern: ElementPattern<UCallExpression>): Self =
    filter { it.getUCallExpression()?.let { callPattern.accepts(it) } ?: false }

  override fun callParameter(parameterIndex: Int, callPattern: ElementPattern<UCallExpression>): Self =
    filter { isCallExpressionParameter(it, parameterIndex, callPattern) }

  override fun constructorParameter(parameterIndex: Int, classFQN: String): Self = callParameter(parameterIndex,
                                                                                                 callExpression().constructor(classFQN))

  override fun setterParameter(methodPattern: ElementPattern<out PsiMethod>): Self = filter {
    isPropertyAssignCall(it, methodPattern) ||
    isCallExpressionParameter(it, 0, callExpression().withAnyResolvedMethod(methodPattern))
  }

  @JvmOverloads
  fun methodCallParameter(parameterIndex: Int, methodPattern: ElementPattern<out PsiMethod>, multiResolve: Boolean = true): Self =
    callParameter(parameterIndex, callExpression().withResolvedMethod(methodPattern, multiResolve))

  override fun arrayAccessParameterOf(receiverClassPattern: ElementPattern<PsiClass>): Self = filter { self ->
    val aae: UArrayAccessExpression = unwrapPolyadic(self).uastParent as? UArrayAccessExpression ?: return@filter false
    val receiverClass = (aae.receiver.getExpressionType() as? PsiClassType)?.resolve() ?: return@filter false
    receiverClassPattern.accepts(receiverClass)
  }

  open class Capture<T : UExpression>(clazz: Class<T>) : UExpressionPattern<T, UExpressionPattern.Capture<T>>(clazz)
}

class ULiteralExpressionPattern : UExpressionPattern<ULiteralExpression, ULiteralExpressionPattern>(ULiteralExpression::class.java)