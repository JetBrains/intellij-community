// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import org.jetbrains.uast.*

internal fun UExpression.extractBranchesResultAsDependency(): Dependency {
  val branchResults = HashSet<UExpression>().apply { accumulateBranchesResult(this) }

  if (branchResults.size > 1)
    return Dependency.BranchingDependency(branchResults)
  return Dependency.CommonDependency(branchResults.firstOrNull() ?: this)
}

private fun UExpression.accumulateBranchesResult(results: MutableSet<UExpression>) {
  when (this) {
    is UIfExpression -> {
      thenExpression?.lastExpression?.accumulateBranchesResult(results)
      elseExpression?.lastExpression?.accumulateBranchesResult(results)
    }
    is USwitchExpression -> body.expressions.filterIsInstance<USwitchClauseExpression>()
      .mapNotNull { it.lastExpression }
      .forEach { it.accumulateBranchesResult(results) }
    is UTryExpression -> {
      tryClause.lastExpression?.accumulateBranchesResult(results)
      catchClauses.mapNotNull { it.body.lastExpression }.forEach { it.accumulateBranchesResult(results) }
    }
    else -> results += this
  }
}

private val UExpression.lastExpression: UExpression?
  get() = when (this) {
    is USwitchClauseExpressionWithBody -> body.expressions.lastOrNull()
    is UBlockExpression -> this.expressions.lastOrNull()
    is UExpressionList -> this.expressions.lastOrNull()
    else -> this
  }?.let { expression ->
    if (expression is UYieldExpression) expression.expression else expression
  }

@Suppress("MemberVisibilityCanBePrivate")
object KotlinExtensionConstants {
  const val STANDARD_CLASS = "kotlin.StandardKt__StandardKt"
  const val LET_METHOD = "let"
  const val ALSO_METHOD = "also"
  const val RUN_METHOD = "run"
  const val APPLY_METHOD = "apply"

  const val LAMBDA_THIS_PARAMETER_NAME = "<this>"

  fun isExtensionWithSideEffect(call: UCallExpression): Boolean =
    call.methodName == ALSO_METHOD || call.methodName == APPLY_METHOD

  fun isLetOrRunCall(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == LET_METHOD || it.methodName == RUN_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS

  fun isAlsoOrApplyCall(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == ALSO_METHOD || it.methodName == APPLY_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS

  fun isExtensionFunctionToIgnore(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == LET_METHOD || it.methodName == ALSO_METHOD || it.methodName == RUN_METHOD || it.methodName == APPLY_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS
}