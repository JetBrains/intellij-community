// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElementType
import org.jetbrains.annotations.ApiStatus
import java.math.BigInteger

/**
 * The operations of Python on [PyConstantValue]. An operand of `null` is not known. An operation gives `null` when its operands
 * do not give a value or when the folder does not know the operation.
 */
@ApiStatus.Internal
object PyConstantFolder {

  /**
   * Applies a binary [operator]. `and` and `or` take `bool` operands only, and they give a value when the known operand decides it,
   * as `True or x` does.
   */
  fun binary(operator: PyElementType, left: PyConstantValue?, right: PyConstantValue?): PyConstantValue? {
    if (operator === PyTokenTypes.AND_KEYWORD || operator === PyTokenTypes.OR_KEYWORD) {
      return logical(operator === PyTokenTypes.OR_KEYWORD, left as? PyConstantValue.Bool, right as? PyConstantValue.Bool)
    }
    return when {
      left is PyConstantValue.Str && right is PyConstantValue.Str -> strBinary(operator, left.value, right.value)
      left is PyConstantValue.Int && right is PyConstantValue.Int -> intBinary(operator, left.value, right.value)
      else -> null
    }
  }

  /**
   * Returns `true` for a prefix [operator] that [prefix] can apply. A caller can then skip the evaluation of the operand
   * for another operator.
   */
  fun isPrefixOperator(operator: PyElementType): Boolean {
    return operator === PyTokenTypes.NOT_KEYWORD || operator === PyTokenTypes.MINUS || operator === PyTokenTypes.PLUS
  }

  /**
   * Applies a prefix [operator]: `not` to a `bool`, and `-` or `+` to an `int`.
   */
  fun prefix(operator: PyElementType, operand: PyConstantValue?): PyConstantValue? {
    return when {
      operator === PyTokenTypes.NOT_KEYWORD && operand is PyConstantValue.Bool -> PyConstantValue.Bool(!operand.value)
      operator === PyTokenTypes.MINUS && operand is PyConstantValue.Int -> PyConstantValue.Int(operand.value.negate())
      operator === PyTokenTypes.PLUS && operand is PyConstantValue.Int -> operand
      else -> null
    }
  }

  /**
   * Returns the result of `bool(value)`.
   */
  fun isTruthy(value: PyConstantValue): Boolean {
    return when (value) {
      is PyConstantValue.Str -> value.value.isNotEmpty()
      is PyConstantValue.Int -> value.value.signum() != 0
      is PyConstantValue.Bool -> value.value
    }
  }

  private fun logical(isOr: Boolean, left: PyConstantValue.Bool?, right: PyConstantValue.Bool?): PyConstantValue.Bool? {
    if (left?.value == isOr || right?.value == isOr) {
      return PyConstantValue.Bool(isOr)
    }
    if (left != null && right != null) {
      return PyConstantValue.Bool(!isOr)
    }
    return null
  }

  private fun strBinary(operator: PyElementType, left: String, right: String): PyConstantValue? {
    return when (operator) {
      PyTokenTypes.PLUS -> PyConstantValue.Str(left + right)
      else -> null
    }
  }

  private fun intBinary(operator: PyElementType, left: BigInteger, right: BigInteger): PyConstantValue? {
    return when (operator) {
      PyTokenTypes.PLUS -> PyConstantValue.Int(left.add(right))
      PyTokenTypes.MINUS -> PyConstantValue.Int(left.subtract(right))
      PyTokenTypes.MULT -> PyConstantValue.Int(left.multiply(right))
      PyTokenTypes.FLOORDIV -> if (right.signum() == 0) null else PyConstantValue.Int(floorDivide(left, right))
      PyTokenTypes.LT -> PyConstantValue.Bool(left < right)
      PyTokenTypes.LE -> PyConstantValue.Bool(left <= right)
      PyTokenTypes.GT -> PyConstantValue.Bool(left > right)
      PyTokenTypes.GE -> PyConstantValue.Bool(left >= right)
      PyTokenTypes.EQEQ -> PyConstantValue.Bool(left.compareTo(right) == 0)
      PyTokenTypes.NE -> PyConstantValue.Bool(left.compareTo(right) != 0)
      else -> null
    }
  }

  /**
   * Rounds the quotient down, as the `//` operator of Python does. [BigInteger.divide] rounds it toward zero.
   */
  private fun floorDivide(dividend: BigInteger, divisor: BigInteger): BigInteger {
    val (quotient, remainder) = dividend.divideAndRemainder(divisor)
    return if (remainder.signum() * divisor.signum() < 0) quotient.subtract(BigInteger.ONE) else quotient
  }
}
