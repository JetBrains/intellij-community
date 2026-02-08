// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UYieldExpression

internal fun UExpression.extractBranchesResultAsDependency(): Dependency {
  val branchResults = HashSet<UExpression>().apply { accumulateBranchesResult(this) }

  if (branchResults.size > 1)
    return Dependency.BranchingDependency(branchResults)
  return Dependency.CommonDependency(branchResults.firstOrNull() ?: this)
}

fun UExpression.accumulateBranchesResult(results: MutableSet<UExpression>) {
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

