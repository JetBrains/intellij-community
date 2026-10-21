// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

/**
 * The known decorators that one decorator can resolve to.
 *
 * Several candidates mean an ambiguous resolve, not several decorators: without the AST the short name of a
 * decorator is all its file gives, and every registered decorator with that short name becomes a candidate.
 * A predicate therefore holds for the decorator when it holds for any one of its candidates.
 *
 * @see PyKnownDecoratorUtil.knownDecoratorsOf
 */
class PyKnownDecorators private constructor(private val candidates: List<PyKnownDecorator>) {

  /** Whether the decorator resolves to a known decorator at all. */
  fun isKnown(): Boolean = candidates.isNotEmpty()

  fun contains(known: PyKnownDecorator): Boolean = known in candidates

  fun containsAny(vararg known: PyKnownDecorator): Boolean = known.any { it in candidates }

  fun hasTrait(trait: Predicate<PyKnownDecorator>): Boolean = candidates.any { trait.test(it) }

  /** Only [PyElementKnownDecorators.findLastWithTrait] needs a candidate rather than an answer about one. */
  @ApiStatus.Internal
  fun findWithTrait(trait: Predicate<PyKnownDecorator>): PyKnownDecorator? = candidates.firstOrNull { trait.test(it) }

  @ApiStatus.Internal
  fun asList(): List<PyKnownDecorator> = candidates

  companion object {
    @JvmField
    val NONE: PyKnownDecorators = PyKnownDecorators(emptyList())

    @ApiStatus.Internal
    @JvmStatic
    fun of(candidates: List<PyKnownDecorator>): PyKnownDecorators =
      if (candidates.isEmpty()) NONE else PyKnownDecorators(candidates.toList())
  }
}
