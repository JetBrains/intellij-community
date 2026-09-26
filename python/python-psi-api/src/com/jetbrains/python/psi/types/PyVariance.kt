// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.types.PyVariance.BIVARIANT
import com.jetbrains.python.psi.types.PyVariance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyVariance.COVARIANT
import com.jetbrains.python.psi.types.PyVariance.INFER_VARIANCE
import com.jetbrains.python.psi.types.PyVariance.INVARIANT
import com.jetbrains.python.psi.types.PyExpectedVariance.NONE
import com.jetbrains.python.psi.types.PyVarianceIntermediateResult.RECURSIVE
import org.jetbrains.annotations.ApiStatus


private fun invertVariance(variance: PyBaseVariance): PyBaseVariance {
  return when (variance) {
    COVARIANT -> CONTRAVARIANT
    CONTRAVARIANT -> COVARIANT
    else -> variance
  }
}


/**
 * The rules are:
 * - Invariant dominates
 * - Covariant × covariant = covariant
 * - Covariant × contravariant = contravariant
 * - Contravariant × contravariant = covariant
 * - Infer_Variance = invariant
 * - Recursive × contravariant = invariant // stable solution
 * - Recursive × other = other
 *
 * Multiplying variances of subtypes of [PyBaseVariance] can narrow the variance type as follows:
 * - [PyExpectedVariance] * [PyBaseVariance] -> [PyExpectedVariance]
 * - [PyVariance] * [PyVarianceIntermediateResult] -> [PyVariance]
 * The related signatures are represented by various overrides in [PyBaseVariance] and its subtypes.
 */
private fun multiplyVariance(first: PyBaseVariance, second: PyBaseVariance): PyBaseVariance {
  return when {
    first == NONE || second == NONE -> NONE
    first == INVARIANT || second == INVARIANT -> INVARIANT
    first == INFER_VARIANCE || second == INFER_VARIANCE -> INVARIANT
    first == RECURSIVE && second == CONTRAVARIANT -> INVARIANT
    first == RECURSIVE -> second
    second == RECURSIVE && first == CONTRAVARIANT -> INVARIANT
    second == RECURSIVE -> first
    first == BIVARIANT -> second
    second == BIVARIANT -> first
    first == second -> COVARIANT
    else -> CONTRAVARIANT
  }
}


/**
 * This is Kotlin acrobatics. The goal is to have
 * (1) a user-friendly enum for variance (i.e., [PyVariance]) and
 * (2) de-facto subclass that also provides the enum [RECURSIVE] when inferred variance is recursive, and
 * (3) de-facto subclass that also provides the enum [NONE] when no expected variance can be computed.
 *
 * The subtype hierarchy looks like:
 *
 * PyBaseVariance
 *  ├─ PyVarianceIntermediateResult
 *  │   ├─ RECURSIVE
 *  │   └─ PyVariance
 *  └─ PyExpectedVariance
 *      ├─ NONE
 *      └─ PyVariance
 */
@ApiStatus.Internal
sealed interface PyBaseVariance {
  val name : String

  operator fun not(): PyBaseVariance = invertVariance(this)

  operator fun times(other: PyBaseVariance): PyBaseVariance = multiplyVariance(this, other)
  operator fun times(other: PyExpectedVariance): PyExpectedVariance = multiplyVariance(this, other) as PyExpectedVariance
}


/** [PyVarianceIntermediateResult] provides the same values as [PyVariance] but with the additional value [RECURSIVE]. */
@ApiStatus.Internal
sealed interface PyVarianceIntermediateResult : PyBaseVariance {
  /**
   * [RECURSIVE] is used to indicate that the variance of a type variable is based on itself, hence recursive.
   * Mind the following illustrating examples:
   *
   * ```Python
   * class C[U]:
   *     def f(self, a: C[U]): pass
   * ```
   * To determine the variance of `U`, we need to check all its uses. Since one use is in `C` itself, the result is recursive.
   *
   * ```Python
   * class M[S]:
   *     def foo(self, b: N[S]): pass
   * class N[T]:
   *     def bar(self, a: M[T]): pass
   * ```
   * To determine the variance of `M`, we need to check all its uses. One use is in `N` which leads to determining the variance of `T`.
   * However, this in turn, has a use in `M` again, which makes the result recursive.
   */
  object RECURSIVE : PyVarianceIntermediateResult {
    override val name: String = "RECURSIVE"
  }

  override operator fun not(): PyVarianceIntermediateResult = super.not() as PyVarianceIntermediateResult
  operator fun times(other: PyVarianceIntermediateResult): PyVarianceIntermediateResult = multiplyVariance(this, other) as PyVarianceIntermediateResult
  operator fun times(other: PyVariance): PyVariance = multiplyVariance(this, other) as PyVariance
}


/** [PyExpectedVariance] provides the same values as [PyVariance] but with the additional value [NONE]. */
@ApiStatus.Internal
sealed interface PyExpectedVariance : PyBaseVariance {
  /**
   * [NONE] is used to indicate that a given position in the AST does not provide an expected variance.
   * Mind the following illustrating example:
   *
   * ```Python
   * from typing import Callable
   * class C[X, Y, Z]:
   *     attr: X
   *     def f(self, a: Callable[[Y], None]) -> Z|None :
   *         Not_Me = None
   * ```
   * The example above shows common locations where uses of type variables (here `X`, `Y`, and `Z`) can occur.
   * These locations are subject to [PyExpectedVarianceJudgment.getExpectedVariance()].
   * In case that method gets called with an invalid location such as `Not_Me`, it will return [NONE].
   */
  object NONE : PyExpectedVariance {
    override val name: String = "NONE"
  }

  override operator fun not(): PyExpectedVariance = super.not() as PyExpectedVariance
  override operator fun times(other: PyBaseVariance): PyExpectedVariance = multiplyVariance(this, other) as PyExpectedVariance

  fun toPyVariance(): PyVariance? {
    return when (this) {
      NONE -> null
      else -> this as PyVariance
    }
  }
}


enum class PyVariance : PyVarianceIntermediateResult, PyExpectedVariance {
  COVARIANT, CONTRAVARIANT, INVARIANT, BIVARIANT, INFER_VARIANCE
  ;

  override operator fun not(): PyVariance = invertVariance(this) as PyVariance
  override operator fun times(other: PyVariance): PyVariance = multiplyVariance(this, other) as PyVariance
  override operator fun times(other: PyVarianceIntermediateResult): PyVariance = multiplyVariance(this, other) as PyVariance

  /**
   * Returns true iff declared/actual variance is compatible with the required/expected variance.
   *
   * Compatibility rules (typical for variance checking):
   * - INFER_VARIANCE is treated as "unknown / don't care" and is compatible with anything.
   * - INVARIANT can be used in both co- and contravariant positions (but not vice versa).
   * - COVARIANT is only compatible with a covariant position.
   * - CONTRAVARIANT is only compatible with a contravariant position.
   * - RECURSIVE does not occur here.
   */
  fun isCompatibleWithActual(actual: PyVariance): Boolean {
    val expected = this
    if (actual == INFER_VARIANCE || expected == INFER_VARIANCE) return true

    return when (expected) {
      COVARIANT -> actual == COVARIANT || actual == INVARIANT
      CONTRAVARIANT -> actual == CONTRAVARIANT || actual == INVARIANT
      INVARIANT -> actual == INVARIANT
      BIVARIANT -> true
    }
  }
}
