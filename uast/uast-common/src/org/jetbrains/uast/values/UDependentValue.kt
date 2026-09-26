// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.values

open class UDependentValue protected constructor(
  value: UValue,
  final override val dependencies: Set<UDependency> = emptySet()
) : UValueBase() {

  val value: UValue = value.unwrap().takeIf { it == UUndeterminedValue } ?: value

  private fun UValue.unwrap() = (this as? UDependentValue)?.unwrap() ?: this

  private fun unwrap(): UValue = value.unwrap()

  private val dependenciesWithThis: Set<UDependency>
    get() = (this as? UDependency)?.let { dependencies + it } ?: dependencies

  private fun wrapBinary(result: UValue, arg: UValue): UValue {
    val wrappedDependencies = (arg as? UDependentValue)?.dependenciesWithThis ?: emptySet()
    val resultDependencies = dependenciesWithThis + wrappedDependencies
    return create(result, resultDependencies)
  }

  private fun wrapUnary(result: UValue) = create(result, dependenciesWithThis)

  override fun plus(other: UValue): UValue = wrapBinary(unwrap() + other.unwrap(), other)

  override fun minus(other: UValue): UValue = wrapBinary(unwrap() - other.unwrap(), other)

  override fun times(other: UValue): UValue = wrapBinary(unwrap() * other.unwrap(), other)

  override fun div(other: UValue): UValue = wrapBinary(unwrap() / other.unwrap(), other)

  internal fun inverseDiv(other: UValue) = wrapBinary(other.unwrap() / unwrap(), other)

  override fun rem(other: UValue): UValue = wrapBinary(unwrap() % other.unwrap(), other)

  internal fun inverseMod(other: UValue) = wrapBinary(other.unwrap() % unwrap(), other)

  override fun unaryMinus(): UValue = wrapUnary(-unwrap())

  override fun valueEquals(other: UValue): UValue = wrapBinary(unwrap() valueEquals other.unwrap(), other)

  override fun valueNotEquals(other: UValue): UValue = wrapBinary(unwrap() valueNotEquals other.unwrap(), other)

  override fun not(): UValue = wrapUnary(!unwrap())

  override fun greater(other: UValue): UValue = wrapBinary(unwrap() greater other.unwrap(), other)

  override fun less(other: UValue): UValue = wrapBinary(other.unwrap() greater unwrap(), other)

  override fun inc(): UValue = wrapUnary(unwrap().inc())

  override fun dec(): UValue = wrapUnary(unwrap().dec())

  override fun and(other: UValue): UValue = wrapBinary(unwrap() and other.unwrap(), other)

  override fun or(other: UValue): UValue = wrapBinary(unwrap() or other.unwrap(), other)

  override fun bitwiseAnd(other: UValue): UValue = wrapBinary(unwrap() bitwiseAnd other.unwrap(), other)

  override fun bitwiseOr(other: UValue): UValue = wrapBinary(unwrap() bitwiseOr other.unwrap(), other)

  override fun bitwiseXor(other: UValue): UValue = wrapBinary(unwrap() bitwiseXor other.unwrap(), other)

  override fun shl(other: UValue): UValue = wrapBinary(unwrap() shl other.unwrap(), other)

  internal fun inverseShiftLeft(other: UValue) = wrapBinary(other.unwrap() shl unwrap(), other)

  override fun shr(other: UValue): UValue = wrapBinary(unwrap() shr other.unwrap(), other)

  internal fun inverseShiftRight(other: UValue) = wrapBinary(other.unwrap() shr unwrap(), other)

  override fun ushr(other: UValue): UValue = wrapBinary(unwrap() ushr other.unwrap(), other)

  internal fun inverseShiftRightUnsigned(other: UValue) =
    wrapBinary(other.unwrap() ushr unwrap(), other)

  override fun merge(other: UValue): UValue = when (other) {
    this -> this
    value -> this
    is UVariableValue -> other.merge(this)
    is UDependentValue -> {
      val allDependencies = dependencies + other.dependencies
      if (value != other.value) UDependentValue(value.merge(other.value), allDependencies)
      else UDependentValue(value, allDependencies)
    }
    else -> UPhiValue.create(this, other)
  }

  override fun toConstant(): UConstant? = value.toConstant()

  internal open fun copy(dependencies: Set<UDependency>) =
    if (dependencies == this.dependencies) this else create(value, dependencies)

  override fun coerceConstant(constant: UConstant): UValue =
    if (toConstant() == constant) this
    else create(value.coerceConstant(constant), dependencies)

  override fun equals(other: Any?): Boolean =
    other is UDependentValue
    && javaClass == other.javaClass
    && value == other.value
    && dependencies == other.dependencies

  override fun hashCode(): Int {
    var result = 31
    result = result * 19 + value.hashCode()
    result = result * 19 + dependencies.hashCode()
    return result
  }

  override fun toString(): String =
    if (dependencies.isNotEmpty())
      "$value" + dependencies.joinToString(prefix = " (depending on: ", postfix = ")", separator = ", ")
    else
      "$value"

  companion object {
    fun create(value: UValue, dependencies: Set<UDependency>): UValue =
      if (dependencies.isNotEmpty()) UDependentValue(value, dependencies)
      else value

    internal fun UValue.coerceConstant(constant: UConstant): UValue =
      (this as? UValueBase)?.coerceConstant(constant) ?: constant
  }
}
