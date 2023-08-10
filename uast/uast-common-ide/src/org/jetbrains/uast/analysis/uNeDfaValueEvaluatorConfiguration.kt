// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.uast.*

fun interface MethodCallEvaluator<T : Any> {
  fun provideValue(evaluator: UNeDfaValueEvaluator<T>, configuration: UNeDfaConfiguration<T>, callExpression: UCallExpression): T?
}

sealed class DslLambdaPlace {
  internal abstract fun getLambda(callExpression: UCallExpression): ULambdaExpression?

  object Last : DslLambdaPlace() {
    override fun getLambda(callExpression: UCallExpression): ULambdaExpression? {
      val method = callExpression.resolve() ?: return null
      val lastIndex = method.parameters.lastIndex
      return callExpression.getArgumentForParameter(lastIndex) as? ULambdaExpression
    }
  }
}

data class DslLambdaDescriptor<T>(
  val lambdaPlace: DslLambdaPlace,
  val lambdaArgumentIndex: Int,
  val lambdaArgumentValueProvider: () -> T?
) {
  internal fun getLambdaParameter(lambda: ULambdaExpression): UParameter? {
    return lambda.parameters.getOrNull(lambdaArgumentIndex)
  }
}

data class DslLikeMethodDescriptor<T>(
  val methodPattern: ElementPattern<PsiMethod>,
  val lambdaDescriptor: DslLambdaDescriptor<T>
) {
  fun accepts(method: PsiMethod?): Boolean = methodPattern.accepts(method)
}

fun interface BuilderMethodEvaluator<T : Any> {
  fun evaluate(call: UCallExpression, value: T?, evaluator: UNeDfaValueEvaluator<T>, configuration: UNeDfaConfiguration<T>, isStrict: Boolean): T?
}

interface BuilderLikeExpressionEvaluator<T : Any> {
  val buildMethod: ElementPattern<PsiMethod>

  val dslBuildMethodDescriptor: DslLikeMethodDescriptor<T>?

  val allowSideEffects: Boolean

  val methodDescriptions: Map<ElementPattern<PsiMethod>, BuilderMethodEvaluator<T>>

  fun isExpressionReturnSelf(expression: UReferenceExpression): Boolean = false
}

data class UNeDfaConfiguration<T : Any>(
  val methodCallDepth: Int = 1,
  val parameterUsagesDepth: Int = 1,
  val valueProviders: Iterable<DeclarationValueEvaluator<T>> = emptyList(),
  val usagesSearchScope: SearchScope = LocalSearchScope.EMPTY,
  val methodsToAnalyzePattern: ElementPattern<PsiMethod> = PlatformPatterns.alwaysFalse(),
  val methodEvaluators: Map<ElementPattern<UCallExpression>, MethodCallEvaluator<T>> = emptyMap(),
  val builderEvaluators: List<BuilderLikeExpressionEvaluator<T>> = emptyList(),
  val calculateNonStaticPrivateFields: Boolean = false,
) {
  internal fun getEvaluatorForCall(callExpression: UCallExpression): MethodCallEvaluator<T>? {
    return methodEvaluators.entries.firstOrNull { (pattern, _) -> pattern.accepts(callExpression) }?.value
  }

  internal fun getBuilderEvaluatorForCall(callExpression: UCallExpression): BuilderLikeExpressionEvaluator<T>? {
    return builderEvaluators.firstOrNull { it.buildMethod.accepts(callExpression.resolve()) }
  }

  internal fun getDslEvaluatorForCall(
    callExpression: UCallExpression
  ): Pair<BuilderLikeExpressionEvaluator<T>, DslLikeMethodDescriptor<T>>? {
    return builderEvaluators.firstOrNull { it.dslBuildMethodDescriptor?.accepts(callExpression.resolve()) == true }
      ?.let { evaluator ->
        evaluator.dslBuildMethodDescriptor?.let { evaluator to it }
      }
  }

  internal fun isAppropriateField(field: UField): Boolean =
    field.isFinal && (field.isStatic || field.visibility == UastVisibility.PRIVATE && calculateNonStaticPrivateFields)
}

fun interface DeclarationValueEvaluator<T : Any> {
  fun provideValue(element: UDeclaration): T?
}