// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

/**
 * The known decorators of a decoratable element, one [PyKnownDecorators] per decorator in declaration order.
 *
 * The two axes stay apart: which decorator, and which known decorator each of them can resolve to. A predicate
 * holds for the element when it holds for any of its decorators, while [allKnown] asks about all of them.
 *
 * @see PyKnownDecoratorUtil.knownDecoratorsOf
 */
class PyElementKnownDecorators private constructor(private val decorators: List<PyKnownDecorators>) {

  /** Whether every decorator of the element resolves to a known decorator. True for an element without decorators. */
  fun allKnown(): Boolean = decorators.all { it.isKnown() }

  fun contains(known: PyKnownDecorator): Boolean = decorators.any { it.contains(known) }

  fun containsAny(vararg known: PyKnownDecorator): Boolean = decorators.any { it.containsAny(*known) }

  fun hasTrait(trait: Predicate<PyKnownDecorator>): Boolean = decorators.any { it.hasTrait(trait) }

  /**
   * Returns the known decorator with [trait] that is nearest the decorated element, or null when none carries it.
   * The decorator nearest the element is applied first and the others wrap it, so it decides.
   */
  fun findLastWithTrait(trait: Predicate<PyKnownDecorator>): PyKnownDecorator? =
    decorators.asReversed().firstNotNullOfOrNull { it.findWithTrait(trait) }

  @ApiStatus.Internal
  fun asList(): List<PyKnownDecorator> = decorators.flatMap { it.asList() }

  companion object {
    @JvmField
    val NONE: PyElementKnownDecorators = PyElementKnownDecorators(emptyList())

    @ApiStatus.Internal
    @JvmStatic
    fun of(decorators: List<PyKnownDecorators>): PyElementKnownDecorators =
      if (decorators.isEmpty()) NONE else PyElementKnownDecorators(decorators.toList())
  }
}
