// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UastPatterns")

package com.intellij.patterns.uast

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ObjectPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PsiJavaPatterns.psiClass
import com.intellij.patterns.PsiNamePatternCondition
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

fun literalExpression(): ULiteralExpressionPattern = ULiteralExpressionPattern()

@JvmOverloads
fun injectionHostUExpression(strict: Boolean = true): UExpressionPattern<UExpression, *> =
  uExpression().filterWithContext { _, processingContext ->
    val requestedPsi = processingContext.get(REQUESTED_PSI_ELEMENT)
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

fun uMethod(): UDeclarationPattern<UMethod> = UDeclarationPattern(UMethod::class.java)

fun uClass(): UDeclarationPattern<UClass> = UDeclarationPattern(UClass::class.java)

fun <T : UElement> capture(clazz: Class<T>): UElementPattern.Capture<T> = UElementPattern.Capture(clazz)

fun <T : UExpression> expressionCapture(clazz: Class<T>): UExpressionPattern.Capture<T> = UExpressionPattern.Capture(clazz)

fun ProcessingContext.withRequestedPsi(psiElement: PsiElement): ProcessingContext = this.apply { put(REQUESTED_PSI_ELEMENT, psiElement) }

fun withRequestedPsi(psiElement: PsiElement): ProcessingContext = ProcessingContext().withRequestedPsi(psiElement)

open class UElementPattern<T : UElement, Self : UElementPattern<T, Self>>(clazz: Class<T>) : ObjectPattern<T, Self>(clazz) {
  fun withSourcePsiCondition(pattern: PatternCondition<PsiElement>): Self =
    this.with(object : PatternCondition<T>("withSourcePsiPattern") {
      override fun accepts(t: T, context: ProcessingContext?): Boolean {
        val sourcePsiElement = t.sourcePsiElement ?: return false
        return pattern.accepts(sourcePsiElement, context)
      }
    })

  fun sourcePsiFilter(filter: (PsiElement) -> Boolean): Self =
    this.with(object : PatternCondition<T>("sourcePsiFilter") {
      override fun accepts(t: T, context: ProcessingContext?): Boolean {
        val sourcePsiElement = t.sourcePsiElement ?: return false
        return filter(sourcePsiElement)
      }
    })

  fun filterWithContext(filter: (T, ProcessingContext) -> Boolean): Self =
    filterWithContext(null, filter)

  fun filterWithContext(debugName: String?, filter: (T, ProcessingContext) -> Boolean): Self =
    with(object : PatternCondition<T>(debugName) {
      override fun accepts(t: T, context: ProcessingContext?): Boolean = filter.invoke(t, context ?: ProcessingContext())
    })

  fun filter(filter: (T) -> Boolean): Self = filterWithContext { t, _ -> filter(t) }

  fun withUastParent(parentPattern: ElementPattern<out UElement>): Self = filterWithContext { it, context ->
    it.uastParent?.let { parentPattern.accepts(it, context) } ?: false
  }

  fun withUastParentOrSelf(parentPattern: ElementPattern<out UElement>): Self = filterWithContext { it, context ->
    parentPattern.accepts(it, context) || it.uastParent?.let { parentPattern.accepts(it, context) } ?: false
  }

  fun withStringRoomExpression(parentPattern: ElementPattern<out UElement>): Self = filterWithContext { it, context ->
    if (it !is UExpression) return@filterWithContext false
    val uHost = it as? UInjectionHost ?: return@filterWithContext false
    val room = uHost.getStringRoomExpression()

    parentPattern.accepts(room, context)
  }

  class Capture<T : UElement>(clazz: Class<T>) : UElementPattern<T, Capture<T>>(clazz)
}

private val constructorOrMethodCall = setOf(UastCallKind.CONSTRUCTOR_CALL, UastCallKind.METHOD_CALL)

private fun isCallExpressionParameter(argumentExpression: UExpression,
                                      parameterIndex: Int,
                                      callPattern: ElementPattern<UCallExpression>, context: ProcessingContext): Boolean {
  val call = argumentExpression.uastParent.getUCallExpression(searchLimit = 2) ?: return false

  return callPattern.accepts(call, context)
         && call.kind in constructorOrMethodCall
         && call.getArgumentForParameter(parameterIndex) == argumentExpression
}

private fun isPropertyAssignCall(argument: UElement, methodPattern: ElementPattern<out PsiMethod>, context: ProcessingContext): Boolean {
  val uBinaryExpression = (argument.uastParent as? UBinaryExpression) ?: return false
  if (uBinaryExpression.operator != UastBinaryOperator.ASSIGN) return false

  val uastReference = uBinaryExpression.leftOperand.asSafely<UReferenceExpression>() ?: return false
  val resolved = uastReference.resolve()
  return methodPattern.accepts(resolved, context)
}

class UCallExpressionPattern : UElementPattern<UCallExpression, UCallExpressionPattern>(UCallExpression::class.java) {

  fun withReceiver(classPattern: ElementPattern<PsiClass>): UCallExpressionPattern =
    filterWithContext { it, context -> (it.receiverType as? PsiClassType)?.takeIf { it.isValid }?.resolve()?.let { classPattern.accepts(it, context) } ?: false }

  fun withMethodName(methodName: String): UCallExpressionPattern = withMethodNames(listOf(methodName))

  fun withAnyResolvedMethod(method: ElementPattern<out PsiMethod>): UCallExpressionPattern = withResolvedMethod(method, true)

  fun withResolvedMethod(method: ElementPattern<out PsiMethod>,
                         multiResolve: Boolean): UCallExpressionPattern {
    val nameCondition = ContainerUtil.findInstance(
      method.condition.conditions,
      PsiNamePatternCondition::class.java)
    return filterWithContext { uCallExpression, context ->
      if (nameCondition != null) {
        val methodName = uCallExpression.methodName
        if (methodName != null && !nameCondition.namePattern.accepts(methodName)) return@filterWithContext false
      }

      if (multiResolve && uCallExpression is UMultiResolvable) {
        uCallExpression.multiResolve().any { method.accepts(it.element, context) }
      }
      else {
        uCallExpression.resolve().let { method.accepts(it, context) }
      }
    }
  }

  @Deprecated(message = "Use withMethodNames with list of names")
  fun withMethodName(namePattern: ElementPattern<String>): UCallExpressionPattern = filterWithContext { it, context ->
    it.methodName?.let {
      namePattern.accepts(it, context)
    } ?: false
  }

  fun withMethodNames(names: Collection<String>): UCallExpressionPattern {
    return filter { it.isMethodNameOneOf(names) }
  }

  fun constructor(classPattern: ElementPattern<PsiClass>, parameterCount: Int): UCallExpressionPattern = filterWithContext { it, context ->
    if (it.classReference == null) return@filterWithContext false

    val psiMethod = it.resolve() ?: return@filterWithContext false

    psiMethod.isConstructor
    && psiMethod.parameterList.parametersCount == parameterCount
    && classPattern.accepts(psiMethod.containingClass, context)
  }

  fun constructor(classPattern: ElementPattern<PsiClass>): UCallExpressionPattern = filterWithContext { it, context ->
    if (it.classReference == null) return@filterWithContext false

    val psiMethod = it.resolve() ?: return@filterWithContext false
    psiMethod.isConstructor && classPattern.accepts(psiMethod.containingClass, context)
  }

  fun constructor(className: String): UCallExpressionPattern = constructor(psiClass().withQualifiedName(className))

  fun constructor(className: String, parameterCount: Int): UCallExpressionPattern = constructor(psiClass().withQualifiedName(className), parameterCount)
}

private val IS_UAST_ANNOTATION_PARAMETER: Key<Boolean> = Key.create("UAST_ANNOTATION_PARAMETER")

open class UExpressionPattern<T : UExpression, Self : UExpressionPattern<T, Self>>(clazz: Class<T>) : UElementPattern<T, Self>(clazz) {

  fun annotationParam(@NonNls parameterName: String, annotationPattern: ElementPattern<UAnnotation>): Self =
    annotationParams(annotationPattern, string().equalTo(parameterName))

  fun annotationParams(annotationPattern: ElementPattern<UAnnotation>, parameterNames: ElementPattern<String>): Self =
    this.with(object : PatternCondition<T>("annotationParam") {
      override fun accepts(uElement: T, context: ProcessingContext?): Boolean {
        val sharedContext = context?.sharedContext
        val isAnnotationParameter = sharedContext?.get(IS_UAST_ANNOTATION_PARAMETER, uElement)
        if (isAnnotationParameter == java.lang.Boolean.FALSE) {
          return false
        }

        val containingUAnnotationEntry = getContainingUAnnotationEntry(uElement)
        if (containingUAnnotationEntry == null) {
          sharedContext?.put(IS_UAST_ANNOTATION_PARAMETER, uElement, java.lang.Boolean.FALSE)
          return false
        }

        return parameterNames.accepts(containingUAnnotationEntry.second ?: "value", context)
               && annotationPattern.accepts(containingUAnnotationEntry.first, context)
      }
    })

  fun annotationParam(annotationQualifiedName: ElementPattern<String>, @NonNls parameterName: String): Self =
    annotationParam(parameterName, uAnnotationQualifiedNamePattern(annotationQualifiedName))

  fun annotationParam(@NonNls annotationQualifiedName: String, @NonNls parameterName: String): Self =
    annotationParam(string().equalTo(annotationQualifiedName), parameterName)

  fun annotationParams(@NonNls annotationQualifiedName: String, @NonNls parameterNames: ElementPattern<String>): Self =
    annotationParams(uAnnotationQualifiedNamePattern(string().equalTo(annotationQualifiedName)), parameterNames)

  fun annotationParams(@NonNls annotationQualifiedNames: List<String>, @NonNls parameterNames: ElementPattern<String>): Self =
    annotationParams(uAnnotationQualifiedNamePattern(string().oneOf(annotationQualifiedNames)), parameterNames)

  fun inCall(callPattern: ElementPattern<UCallExpression>): Self =
    filterWithContext { it, context -> it.getUCallExpression()?.let { callPattern.accepts(it, context) } ?: false }

  fun callParameter(parameterIndex: Int, callPattern: ElementPattern<UCallExpression>): Self =
    filterWithContext { t, processingContext -> isCallExpressionParameter(t, parameterIndex, callPattern, processingContext) }

  fun constructorParameter(parameterIndex: Int, classFQN: String): Self =
    callParameter(parameterIndex, callExpression().constructor(classFQN))

  fun setterParameter(methodPattern: ElementPattern<out PsiMethod>): Self = filterWithContext { it, context ->
    isPropertyAssignCall(it, methodPattern, context) ||
    isCallExpressionParameter(it, 0, callExpression().withAnyResolvedMethod(methodPattern), context)
  }

  @JvmOverloads
  fun methodCallParameter(parameterIndex: Int, methodPattern: ElementPattern<out PsiMethod>, multiResolve: Boolean = true): Self =
    callParameter(parameterIndex, callExpression().withResolvedMethod(methodPattern, multiResolve))

  fun arrayAccessParameterOf(receiverClassPattern: ElementPattern<PsiClass>): Self = filterWithContext { self, context ->
    val aae: UArrayAccessExpression = self.uastParent as? UArrayAccessExpression ?: return@filterWithContext false
    val receiverClass = (aae.receiver.getExpressionType() as? PsiClassType)?.resolve() ?: return@filterWithContext false
    receiverClassPattern.accepts(receiverClass, context)
  }

  fun inside(strict: Boolean, parentPattern: ElementPattern<out UElement>): Self {
    return this.with(object : PatternCondition<T>("inside") {
      override fun accepts(element: T, context: ProcessingContext?): Boolean {
        if (strict) {
          return parentPattern.accepts(element.uastParent)
        }

        var parent = element.uastParent
        while (parent != null) {
          if (parentPattern.accepts(parent)) {
            return true
          }
          parent = parent.uastParent
        }

        return false
      }
    })
  }

  open class Capture<T : UExpression>(clazz: Class<T>) : UExpressionPattern<T, Capture<T>>(clazz)
}

class ULiteralExpressionPattern : UExpressionPattern<ULiteralExpression, ULiteralExpressionPattern>(ULiteralExpression::class.java)

fun uAnnotationQualifiedNamePattern(annotationQualifiedName: ElementPattern<String>): UElementPattern<UAnnotation, *> =
  capture(UAnnotation::class.java).filterWithContext(annotationQualifiedName.toString()) { it, context ->
    it.qualifiedName?.let {
      annotationQualifiedName.accepts(it, context)
    } ?: false
  }

open class UDeclarationPattern<T : UDeclaration>(clazz: Class<T>) : UElementPattern<T, UDeclarationPattern<T>>(clazz) {

  fun annotatedWith(@NotNull annotationQualifiedNames: List<String>): UDeclarationPattern<T> {
    return this.with(object : PatternCondition<UDeclaration>("annotatedWith") {
      override fun accepts(uDeclaration: UDeclaration, context: ProcessingContext?): Boolean {
        return uDeclaration.uAnnotations.any { uAnno ->
          annotationQualifiedNames.any { annoFqn ->
            annoFqn == uAnno.qualifiedName
          }
        }
      }
    })
  }
}