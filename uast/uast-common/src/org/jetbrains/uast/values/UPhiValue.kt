// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.values

class UPhiValue private constructor(val values: Set<UValue>) : UValueBase() {

  override val dependencies: Set<UDependency> = values.flatMapTo(linkedSetOf()) { it.dependencies }

  override fun equals(other: Any?): Boolean = other is UPhiValue && values == other.values

  override fun hashCode(): Int = values.hashCode()

  override fun toString(): String = values.joinToString(prefix = "Phi(", postfix = ")", separator = ", ")

  override val reachable: Boolean
    get() = values.any { it.reachable }

  companion object {
    private const val PHI_LIMIT = 4

    fun create(values: Iterable<UValue>): UValue {
      val flattenedValues = values.flatMapTo(linkedSetOf<UValue>()) { (it as? UPhiValue)?.values ?: listOf(it) }
      if (flattenedValues.size <= 1) {
        throw AssertionError("UPhiValue should contain two or more values: $flattenedValues")
      }
      if (flattenedValues.size > PHI_LIMIT || UUndeterminedValue in flattenedValues) {
        return UUndeterminedValue
      }
      return UPhiValue(flattenedValues)
    }

    fun create(vararg values: UValue): UValue = create(values.asIterable())
  }
}

