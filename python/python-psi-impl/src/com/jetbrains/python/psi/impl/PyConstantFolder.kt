// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElementType
import org.jetbrains.annotations.ApiStatus
import java.math.BigInteger

/**
 * The operations of Python on [PyConstantValue]. An operand of `null` is not known. An operation gives `null` when Python raises
 * an error, when Python gives a value of a kind that [PyConstantValue] does not hold, when the result is larger than the limits,
 * or when the folder does not know the operation.
 *
 * Each operation follows CPython. A `str` counts code points, not UTF-16 units. A `bool` acts as the `int` 0 or 1 in arithmetic.
 */
@ApiStatus.Internal
object PyConstantFolder {

  /** The largest `str` result, in UTF-16 units. */
  const val MAX_STRING_LENGTH: Int = 10_000

  /** The largest `int` result, in bits. */
  const val MAX_INT_BIT_LENGTH: Int = 8_192

  /** The largest `list` or `tuple` result. */
  const val MAX_SEQUENCE_SIZE: Int = 10_000

  /**
   * Applies a binary [operator]. `and` and `or` return an operand, as in Python, so they need a known left operand.
   * A chained comparison such as `a < b < c` is not one binary operation: the caller applies each link.
   */
  fun binary(operator: PyElementType, left: PyConstantValue?, right: PyConstantValue?): PyConstantValue? {
    if (operator === PyTokenTypes.AND_KEYWORD || operator === PyTokenTypes.OR_KEYWORD) {
      return logical(operator === PyTokenTypes.OR_KEYWORD, left, right)
    }
    if (left == null || right == null) {
      return null
    }
    return checked(when (operator) {
      PyTokenTypes.EQEQ -> equals(left, right)?.let { PyConstantValue.Bool(it) }
      PyTokenTypes.NE -> equals(left, right)?.let { PyConstantValue.Bool(!it) }
      PyTokenTypes.LT -> compare(left, right)?.let { PyConstantValue.Bool(it < 0) }
      PyTokenTypes.LE -> compare(left, right)?.let { PyConstantValue.Bool(it <= 0) }
      PyTokenTypes.GT -> compare(left, right)?.let { PyConstantValue.Bool(it > 0) }
      PyTokenTypes.GE -> compare(left, right)?.let { PyConstantValue.Bool(it >= 0) }
      PyTokenTypes.PLUS -> add(left, right)
      PyTokenTypes.MULT -> multiply(left, right)
      else -> {
        val first = asInteger(left) ?: return null
        val second = asInteger(right) ?: return null
        if (left is PyConstantValue.Bool && right is PyConstantValue.Bool) {
          bitwiseOfBools(operator, left.value, right.value)?.let { return it }
        }
        intBinary(operator, first, second)
      }
    })
  }

  /**
   * Returns `true` for a prefix [operator] that [prefix] can apply. A caller can then skip the evaluation of the operand
   * for another operator.
   */
  fun isPrefixOperator(operator: PyElementType): Boolean {
    return operator === PyTokenTypes.NOT_KEYWORD ||
           operator === PyTokenTypes.MINUS ||
           operator === PyTokenTypes.PLUS ||
           operator === PyTokenTypes.TILDE
  }

  /**
   * Applies a prefix [operator]: `not` to any value, `-` or `+` to an `int` or a `bool`, and `~` to an `int`.
   * Python 3.16 removes `~` on a `bool`, so it gives no value.
   */
  fun prefix(operator: PyElementType, operand: PyConstantValue?): PyConstantValue? {
    if (operand == null) {
      return null
    }
    if (operator === PyTokenTypes.NOT_KEYWORD) {
      return PyConstantValue.Bool(!isTruthy(operand))
    }
    if (operator === PyTokenTypes.TILDE && operand is PyConstantValue.Bool) {
      return null
    }
    val value = asInteger(operand) ?: return null
    return when (operator) {
      PyTokenTypes.MINUS -> PyConstantValue.Int(value.negate())
      PyTokenTypes.PLUS -> PyConstantValue.Int(value)
      PyTokenTypes.TILDE -> PyConstantValue.Int(value.not())
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
      is PyConstantValue.Sequence -> value.items.isNotEmpty()
    }
  }

  /**
   * Returns the result of `item in container`, or `null` when Python raises an error or the result is not known.
   */
  fun contains(container: PyConstantValue, item: PyConstantValue): Boolean? {
    return when (container) {
      is PyConstantValue.Str -> (item as? PyConstantValue.Str)?.let { container.value.contains(it.value) }
      is PyConstantValue.Sequence -> {
        var unknown = false
        for (element in container.items) {
          when (equals(element, item)) {
            true -> return true
            null -> unknown = true
            false -> {}
          }
        }
        if (unknown) null else false
      }
      is PyConstantValue.Int, is PyConstantValue.Bool -> null
    }
  }

  /**
   * Returns the result of `value[index]` for a `str`, a `list` or a `tuple`.
   */
  fun subscript(value: PyConstantValue, index: PyConstantValue): PyConstantValue? {
    val position = asInteger(index) ?: return null
    return when (value) {
      is PyConstantValue.Str -> {
        val codePoints = value.value.codePoints().toArray()
        normalizeIndex(position, codePoints.size)?.let { PyConstantValue.Str(String(codePoints, it, 1)) }
      }
      is PyConstantValue.Sequence -> normalizeIndex(position, value.items.size)?.let { value.items[it] }
      is PyConstantValue.Int, is PyConstantValue.Bool -> null
    }
  }

  /**
   * Returns the result of `value[start:stop:step]` for a `str`, a `list` or a `tuple`. A bound of `null` is absent from the slice.
   */
  fun slice(value: PyConstantValue, start: BigInteger?, stop: BigInteger?, step: BigInteger?): PyConstantValue? {
    return when (value) {
      is PyConstantValue.Str -> {
        val codePoints = value.value.codePoints().toArray()
        val indices = sliceIndices(codePoints.size, start, stop, step) ?: return null
        PyConstantValue.Str(String(IntArray(indices.size) { codePoints[indices[it]] }, 0, indices.size))
      }
      is PyConstantValue.Sequence -> {
        val indices = sliceIndices(value.items.size, start, stop, step) ?: return null
        PyConstantValue.Sequence(indices.map { value.items[it] }, value.kind)
      }
      is PyConstantValue.Int, is PyConstantValue.Bool -> null
    }
  }

  /**
   * Returns the result of `str(value)`, or `null` for a value whose `str` is its `repr`.
   */
  fun toStr(value: PyConstantValue): String? {
    return when (value) {
      is PyConstantValue.Str -> value.value
      is PyConstantValue.Int -> value.value.toString()
      is PyConstantValue.Bool -> if (value.value) "True" else "False"
      is PyConstantValue.Sequence -> null
    }
  }

  /**
   * Calls the method [name] of a `str` [receiver] with positional [arguments]. The folder knows `upper`, `lower`, `casefold`, `title`,
   * `capitalize`, `strip`, `lstrip`, `rstrip`, `split`, `join`, `startswith`, `endswith`, `removeprefix`, `removesuffix`, `zfill`
   * and `replace`. `casefold`, `title` and `capitalize` give no value for a non-ASCII `str`, because their full Unicode mappings
   * differ from those of Java.
   */
  fun callMethod(receiver: PyConstantValue, name: String, arguments: List<PyConstantValue?>): PyConstantValue? {
    if (receiver !is PyConstantValue.Str || arguments.any { it == null }) {
      return null
    }
    @Suppress("UNCHECKED_CAST")
    return checked(PyStrMethods.call(receiver.value, name, arguments as List<PyConstantValue>))
  }

  /**
   * Calls the builtin function [name] with positional [arguments]. The folder knows `len`, `str`, `int`, `bool`, `ord` and `chr`.
   */
  fun callBuiltin(name: String, arguments: List<PyConstantValue?>): PyConstantValue? {
    if (arguments.any { it == null }) {
      return null
    }
    @Suppress("UNCHECKED_CAST")
    val values = arguments as List<PyConstantValue>
    return checked(when (name) {
      "len" -> values.singleOrNull()?.let(::length)?.let { PyConstantValue.Int(BigInteger.valueOf(it.toLong())) }
      "str" -> when (values.size) {
        0 -> PyConstantValue.Str("")
        1 -> toStr(values[0])?.let { PyConstantValue.Str(it) }
        else -> null
      }
      "bool" -> when (values.size) {
        0 -> PyConstantValue.Bool(false)
        1 -> PyConstantValue.Bool(isTruthy(values[0]))
        else -> null
      }
      "int" -> when (values.size) {
        0 -> PyConstantValue.Int(BigInteger.ZERO)
        1 -> toInt(values[0])?.let { PyConstantValue.Int(it) }
        else -> null
      }
      "ord" -> (values.singleOrNull() as? PyConstantValue.Str)?.value?.let { text ->
        if (text.codePointCount(0, text.length) == 1) PyConstantValue.Int(BigInteger.valueOf(text.codePointAt(0).toLong())) else null
      }
      // A surrogate gives no value, because Java joins two surrogates into one code point and Python does not.
      "chr" -> values.singleOrNull()?.let(::asInteger)?.let { codePoint ->
        val isSurrogate = codePoint >= BigInteger.valueOf(Character.MIN_SURROGATE.code.toLong()) &&
                          codePoint <= BigInteger.valueOf(Character.MAX_SURROGATE.code.toLong())
        if (codePoint.signum() >= 0 && codePoint <= BigInteger.valueOf(Character.MAX_CODE_POINT.toLong()) && !isSurrogate) {
          PyConstantValue.Str(String(Character.toChars(codePoint.toInt())))
        }
        else null
      }
      else -> null
    })
  }

  /**
   * Returns the value of an `int` or a `bool` as an integer.
   */
  internal fun asInteger(value: PyConstantValue): BigInteger? {
    return when (value) {
      is PyConstantValue.Int -> value.value
      is PyConstantValue.Bool -> if (value.value) BigInteger.ONE else BigInteger.ZERO
      is PyConstantValue.Str, is PyConstantValue.Sequence -> null
    }
  }

  private fun logical(isOr: Boolean, left: PyConstantValue?, right: PyConstantValue?): PyConstantValue? {
    if (left == null) {
      return null
    }
    return if (isTruthy(left) == isOr) left else right
  }

  private fun equals(left: PyConstantValue, right: PyConstantValue): Boolean? {
    val first = asInteger(left)
    val second = asInteger(right)
    if (first != null && second != null) {
      return first.compareTo(second) == 0
    }
    if (left is PyConstantValue.Str && right is PyConstantValue.Str) {
      return left.value == right.value
    }
    if (left is PyConstantValue.Sequence && right is PyConstantValue.Sequence) {
      if (left.kind != right.kind || left.items.size != right.items.size) {
        return false
      }
      var unknown = false
      for (i in left.items.indices) {
        when (equals(left.items[i], right.items[i])) {
          false -> return false
          null -> unknown = true
          true -> {}
        }
      }
      return if (unknown) null else true
    }
    return false
  }

  private fun compare(left: PyConstantValue, right: PyConstantValue): Int? {
    val first = asInteger(left)
    val second = asInteger(right)
    if (first != null && second != null) {
      return first.compareTo(second)
    }
    if (left is PyConstantValue.Str && right is PyConstantValue.Str) {
      return compareCodePoints(left.value, right.value)
    }
    return null
  }

  private fun compareCodePoints(left: String, right: String): Int {
    val first = left.codePoints().toArray()
    val second = right.codePoints().toArray()
    for (i in 0 until minOf(first.size, second.size)) {
      if (first[i] != second[i]) {
        return first[i].compareTo(second[i])
      }
    }
    return first.size.compareTo(second.size)
  }

  private fun add(left: PyConstantValue, right: PyConstantValue): PyConstantValue? {
    if (left is PyConstantValue.Str && right is PyConstantValue.Str) {
      if (left.value.length.toLong() + right.value.length > MAX_STRING_LENGTH) return null
      return PyConstantValue.Str(left.value + right.value)
    }
    if (left is PyConstantValue.Sequence && right is PyConstantValue.Sequence) {
      return if (left.kind == right.kind) PyConstantValue.Sequence(left.items + right.items, left.kind) else null
    }
    val first = asInteger(left) ?: return null
    val second = asInteger(right) ?: return null
    return PyConstantValue.Int(first.add(second))
  }

  private fun multiply(left: PyConstantValue, right: PyConstantValue): PyConstantValue? {
    if (left is PyConstantValue.Str) {
      return repeat(left.value, asInteger(right) ?: return null)
    }
    if (right is PyConstantValue.Str) {
      return repeat(right.value, asInteger(left) ?: return null)
    }
    val first = asInteger(left) ?: return null
    val second = asInteger(right) ?: return null
    return PyConstantValue.Int(first.multiply(second))
  }

  private fun repeat(text: String, count: BigInteger): PyConstantValue.Str? {
    if (count.signum() <= 0 || text.isEmpty()) {
      return PyConstantValue.Str("")
    }
    if (count > BigInteger.valueOf((MAX_STRING_LENGTH / text.length).toLong())) {
      return null
    }
    return PyConstantValue.Str(text.repeat(count.toInt()))
  }

  private fun bitwiseOfBools(operator: PyElementType, left: Boolean, right: Boolean): PyConstantValue.Bool? {
    return when (operator) {
      PyTokenTypes.AND -> PyConstantValue.Bool(left and right)
      PyTokenTypes.OR -> PyConstantValue.Bool(left or right)
      PyTokenTypes.XOR -> PyConstantValue.Bool(left xor right)
      else -> null
    }
  }

  private fun intBinary(operator: PyElementType, left: BigInteger, right: BigInteger): PyConstantValue? {
    return when (operator) {
      PyTokenTypes.MINUS -> PyConstantValue.Int(left.subtract(right))
      PyTokenTypes.FLOORDIV -> if (right.signum() == 0) null else PyConstantValue.Int(floorDivide(left, right))
      PyTokenTypes.PERC -> if (right.signum() == 0) null else PyConstantValue.Int(floorModulo(left, right))
      PyTokenTypes.EXP -> power(left, right)
      PyTokenTypes.AND -> PyConstantValue.Int(left.and(right))
      PyTokenTypes.OR -> PyConstantValue.Int(left.or(right))
      PyTokenTypes.XOR -> PyConstantValue.Int(left.xor(right))
      PyTokenTypes.LTLT -> shiftLeft(left, right)
      PyTokenTypes.GTGT -> shiftRight(left, right)
      else -> null
    }
  }

  /**
   * A negative exponent gives a `float`, which [PyConstantValue] does not hold.
   */
  private fun power(base: BigInteger, exponent: BigInteger): PyConstantValue.Int? {
    if (exponent.signum() < 0) {
      return null
    }
    if (base.abs() <= BigInteger.ONE) {
      val odd = exponent.testBit(0)
      return PyConstantValue.Int(when {
        base.signum() == 0 -> if (exponent.signum() == 0) BigInteger.ONE else BigInteger.ZERO
        base == BigInteger.ONE -> BigInteger.ONE
        else -> if (odd) BigInteger.ONE.negate() else BigInteger.ONE
      })
    }
    if (exponent > BigInteger.valueOf((MAX_INT_BIT_LENGTH / (base.bitLength() - 1).coerceAtLeast(1)).toLong())) {
      return null
    }
    return PyConstantValue.Int(base.pow(exponent.toInt()))
  }

  private fun shiftLeft(value: BigInteger, count: BigInteger): PyConstantValue.Int? {
    if (count.signum() < 0) {
      return null
    }
    if (value.signum() == 0) {
      return PyConstantValue.Int(BigInteger.ZERO)
    }
    if (count > BigInteger.valueOf(MAX_INT_BIT_LENGTH.toLong())) {
      return null
    }
    return PyConstantValue.Int(value.shiftLeft(count.toInt()))
  }

  private fun shiftRight(value: BigInteger, count: BigInteger): PyConstantValue.Int? {
    if (count.signum() < 0) {
      return null
    }
    if (count > BigInteger.valueOf(value.bitLength().toLong())) {
      return PyConstantValue.Int(if (value.signum() < 0) BigInteger.ONE.negate() else BigInteger.ZERO)
    }
    return PyConstantValue.Int(value.shiftRight(count.toInt()))
  }

  /**
   * Rounds the quotient down, as the `//` operator of Python does. [BigInteger.divide] rounds it toward zero.
   */
  private fun floorDivide(dividend: BigInteger, divisor: BigInteger): BigInteger {
    val (quotient, remainder) = dividend.divideAndRemainder(divisor)
    return if (remainder.signum() * divisor.signum() < 0) quotient.subtract(BigInteger.ONE) else quotient
  }

  /**
   * Gives the remainder the sign of the divisor, as the `%` operator of Python does.
   */
  private fun floorModulo(dividend: BigInteger, divisor: BigInteger): BigInteger {
    val remainder = dividend.rem(divisor)
    return if (remainder.signum() * divisor.signum() < 0) remainder.add(divisor) else remainder
  }

  private fun length(value: PyConstantValue): Int? {
    return when (value) {
      is PyConstantValue.Str -> value.value.codePointCount(0, value.value.length)
      is PyConstantValue.Sequence -> value.items.size
      is PyConstantValue.Int, is PyConstantValue.Bool -> null
    }
  }

  /**
   * Converts a value as `int(value)` does. A `str` may hold ASCII digits with single underscores between them,
   * an optional sign and the whitespace of [PyStrMethods.stripIntWhitespace] around them.
   */
  private fun toInt(value: PyConstantValue): BigInteger? {
    return when (value) {
      is PyConstantValue.Int, is PyConstantValue.Bool -> asInteger(value)
      is PyConstantValue.Str -> {
        val text = PyStrMethods.stripIntWhitespace(value.value)
        if (!INT_LITERAL.matches(text) || text.length > MAX_INT_BIT_LENGTH / 3) null else BigInteger(text.replace("_", ""))
      }
      is PyConstantValue.Sequence -> null
    }
  }

  private val INT_LITERAL = Regex("[+-]?[0-9]+(_[0-9]+)*")

  private fun normalizeIndex(index: BigInteger, size: Int): Int? {
    val normalized = if (index.signum() < 0) index.add(BigInteger.valueOf(size.toLong())) else index
    return if (normalized.signum() >= 0 && normalized < BigInteger.valueOf(size.toLong())) normalized.toInt() else null
  }

  /**
   * Computes the selected indices as `slice.indices` and `range` do. A step of zero raises an error in Python.
   */
  private fun sliceIndices(size: Int, start: BigInteger?, stop: BigInteger?, step: BigInteger?): List<Int>? {
    val stepValue = step ?: BigInteger.ONE
    if (stepValue.signum() == 0) {
      return null
    }
    val length = BigInteger.valueOf(size.toLong())
    val negativeStep = stepValue.signum() < 0
    val lower = if (negativeStep) BigInteger.ONE.negate() else BigInteger.ZERO
    val upper = if (negativeStep) length.subtract(BigInteger.ONE) else length

    fun bound(value: BigInteger?, default: BigInteger): BigInteger {
      if (value == null) return default
      return if (value.signum() < 0) value.add(length).max(lower) else value.min(upper)
    }

    val first = bound(start, if (negativeStep) upper else lower).toLong()
    val last = bound(stop, if (negativeStep) lower else upper).toLong()
    val stepLong = stepValue.max(BigInteger.valueOf(-size.toLong() - 1)).min(BigInteger.valueOf(size.toLong() + 1)).toLong()
    val result = ArrayList<Int>()
    var i = first
    while (if (negativeStep) i > last else i < last) {
      result.add(i.toInt())
      i += stepLong
    }
    return result
  }

  private fun checked(value: PyConstantValue?): PyConstantValue? {
    return when (value) {
      null -> null
      is PyConstantValue.Str -> if (value.value.length > MAX_STRING_LENGTH) null else value
      is PyConstantValue.Int -> if (value.value.bitLength() > MAX_INT_BIT_LENGTH) null else value
      is PyConstantValue.Bool -> value
      is PyConstantValue.Sequence -> if (value.items.size > MAX_SEQUENCE_SIZE) null else value
    }
  }
}
