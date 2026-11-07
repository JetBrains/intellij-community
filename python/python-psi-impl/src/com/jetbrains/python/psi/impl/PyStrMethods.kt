// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import java.math.BigInteger

/**
 * The methods of `str` for [PyConstantFolder.callMethod]. Each method follows CPython, and it gives `null` when CPython raises an error.
 */
internal object PyStrMethods {

  fun call(receiver: String, name: String, arguments: List<PyConstantValue>): PyConstantValue? {
    return when (name) {
      // The Unicode version of Java can differ from that of the target Python for recently added characters.
      "upper" -> noArguments(arguments) { PyConstantValue.Str(receiver.uppercase()) }
      "lower" -> noArguments(arguments) { PyConstantValue.Str(receiver.lowercase()) }
      "casefold" -> noArguments(arguments) { ifAscii(receiver) { it.lowercase() } }
      "title" -> noArguments(arguments) { ifAscii(receiver, ::title) }
      "capitalize" -> noArguments(arguments) { ifAscii(receiver, ::capitalize) }
      "strip" -> strip(receiver, arguments, leading = true, trailing = true)
      "lstrip" -> strip(receiver, arguments, leading = true, trailing = false)
      "rstrip" -> strip(receiver, arguments, leading = false, trailing = true)
      "split" -> split(receiver, arguments)
      "join" -> join(receiver, arguments)
      "startswith" -> affix(receiver, arguments) { text, affix -> text.startsWith(affix) }
      "endswith" -> affix(receiver, arguments) { text, affix -> text.endsWith(affix) }
      "removeprefix" -> singleStr(arguments)?.let { PyConstantValue.Str(receiver.removePrefix(it)) }
      "removesuffix" -> singleStr(arguments)?.let { PyConstantValue.Str(receiver.removeSuffix(it)) }
      "zfill" -> zfill(receiver, arguments)
      "replace" -> replace(receiver, arguments)
      else -> null
    }
  }

  /**
   * Removes the whitespace that `int(text)` accepts from both ends. `int` accepts ASCII whitespace without `\x1c` to `\x1f`,
   * and the non-ASCII characters of [isPythonWhitespace].
   */
  fun stripIntWhitespace(text: String): String {
    return stripCodePoints(text, leading = true, trailing = true) { it in 0x09..0x0D || it == 0x20 || it >= 0x80 && isPythonWhitespace(it) }
  }

  /**
   * The characters that `str.isspace()` accepts in CPython. Java's [Character.isWhitespace] rejects some of them, such as ` `.
   */
  fun isPythonWhitespace(codePoint: Int): Boolean {
    return codePoint in 0x09..0x0D || codePoint in 0x1C..0x20 || codePoint == 0x85 || codePoint == 0xA0 || codePoint == 0x1680 ||
           codePoint in 0x2000..0x200A || codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0x202F || codePoint == 0x205F ||
           codePoint == 0x3000
  }

  private inline fun noArguments(arguments: List<PyConstantValue>, compute: () -> PyConstantValue?): PyConstantValue? {
    return if (arguments.isEmpty()) compute() else null
  }

  private inline fun ifAscii(text: String, transform: (String) -> String): PyConstantValue? {
    return if (text.all { it.code < 0x80 }) PyConstantValue.Str(transform(text)) else null
  }

  private fun singleStr(arguments: List<PyConstantValue>): String? {
    return (arguments.singleOrNull() as? PyConstantValue.Str)?.value
  }

  /**
   * Upper-cases the first letter of each word and lower-cases the rest. A word starts after a character that has no case,
   * so `"they're".title()` gives `"They'Re"`.
   */
  private fun title(text: String): String {
    val result = StringBuilder(text.length)
    var previousIsCased = false
    for (char in text) {
      result.append(if (previousIsCased) char.lowercaseChar() else char.uppercaseChar())
      previousIsCased = char in 'a'..'z' || char in 'A'..'Z'
    }
    return result.toString()
  }

  private fun capitalize(text: String): String {
    return if (text.isEmpty()) text else text.substring(0, 1).uppercase() + text.substring(1).lowercase()
  }

  private fun strip(receiver: String, arguments: List<PyConstantValue>, leading: Boolean, trailing: Boolean): PyConstantValue? {
    return when (arguments.size) {
      0 -> PyConstantValue.Str(stripCodePoints(receiver, leading, trailing, ::isPythonWhitespace))
      1 -> {
        val characters = (arguments[0] as? PyConstantValue.Str)?.value?.codePoints()?.toArray()?.toSet() ?: return null
        PyConstantValue.Str(stripCodePoints(receiver, leading, trailing) { it in characters })
      }
      else -> null
    }
  }

  private inline fun stripCodePoints(text: String, leading: Boolean, trailing: Boolean, remove: (Int) -> Boolean): String {
    val codePoints = text.codePoints().toArray()
    var start = 0
    var end = codePoints.size
    if (leading) {
      while (start < end && remove(codePoints[start])) start++
    }
    if (trailing) {
      while (end > start && remove(codePoints[end - 1])) end--
    }
    return String(codePoints, start, end - start)
  }

  /**
   * Splits as `str.split(sep, maxsplit)` does. Without a separator, it splits at runs of Python whitespace and drops empty parts.
   */
  private fun split(receiver: String, arguments: List<PyConstantValue>): PyConstantValue? {
    if (arguments.size > 2) {
      return null
    }
    val separator = arguments.getOrNull(0)?.let { (it as? PyConstantValue.Str)?.value ?: return null }
    val maxSplit = arguments.getOrNull(1)?.let { PyConstantFolder.asInteger(it) ?: return null } ?: BigInteger.ONE.negate()
    val limit = if (maxSplit.signum() < 0 || maxSplit > BigInteger.valueOf(Int.MAX_VALUE.toLong())) Int.MAX_VALUE else maxSplit.toInt()
    val parts = when {
      separator == null -> splitAtWhitespace(receiver, limit)
      separator.isEmpty() -> return null
      limit == Int.MAX_VALUE -> receiver.split(separator)
      else -> receiver.split(separator, limit = limit + 1)
    }
    return PyConstantValue.Sequence(parts.map { PyConstantValue.Str(it) }, PyConstantValue.SequenceKind.LIST)
  }

  private fun splitAtWhitespace(text: String, limit: Int): List<String> {
    val codePoints = text.codePoints().toArray()
    val size = codePoints.size
    val result = ArrayList<String>()
    var i = 0
    var count = 0
    while (count < limit) {
      while (i < size && isPythonWhitespace(codePoints[i])) i++
      if (i == size) break
      val start = i
      while (i < size && !isPythonWhitespace(codePoints[i])) i++
      result.add(String(codePoints, start, i - start))
      count++
    }
    if (i < size) {
      while (i < size && isPythonWhitespace(codePoints[i])) i++
      if (i != size) result.add(String(codePoints, i, size - i))
    }
    return result
  }

  private fun join(receiver: String, arguments: List<PyConstantValue>): PyConstantValue? {
    val sequence = arguments.singleOrNull() as? PyConstantValue.Sequence ?: return null
    val parts = sequence.items.map { (it as? PyConstantValue.Str)?.value ?: return null }
    if (parts.sumOf { it.length.toLong() } + receiver.length.toLong() * parts.size > PyConstantFolder.MAX_STRING_LENGTH) {
      return null
    }
    return PyConstantValue.Str(parts.joinToString(receiver))
  }

  /**
   * Checks a `str` affix or a `tuple` of `str` affixes. The folder does not know the start and end arguments.
   */
  private inline fun affix(receiver: String, arguments: List<PyConstantValue>, test: (String, String) -> Boolean): PyConstantValue? {
    val affixes = when (val argument = arguments.singleOrNull()) {
      is PyConstantValue.Str -> listOf(argument.value)
      is PyConstantValue.Sequence -> {
        if (argument.kind != PyConstantValue.SequenceKind.TUPLE) return null
        argument.items.map { (it as? PyConstantValue.Str)?.value ?: return null }
      }
      else -> return null
    }
    return PyConstantValue.Bool(affixes.any { test(receiver, it) })
  }

  private fun zfill(receiver: String, arguments: List<PyConstantValue>): PyConstantValue? {
    val width = PyConstantFolder.asInteger(arguments.singleOrNull() ?: return null) ?: return null
    val length = receiver.codePointCount(0, receiver.length)
    if (width <= BigInteger.valueOf(length.toLong())) {
      return PyConstantValue.Str(receiver)
    }
    if (width > BigInteger.valueOf(PyConstantFolder.MAX_STRING_LENGTH.toLong())) {
      return null
    }
    val zeros = "0".repeat(width.toInt() - length)
    val hasSign = receiver.startsWith('+') || receiver.startsWith('-')
    return PyConstantValue.Str(if (hasSign) receiver[0] + zeros + receiver.substring(1) else zeros + receiver)
  }

  /**
   * Replaces as `str.replace(old, new, count)` does. An empty `old` inserts `new` around each code point.
   */
  private fun replace(receiver: String, arguments: List<PyConstantValue>): PyConstantValue? {
    if (arguments.size !in 2..3) {
      return null
    }
    val old = (arguments[0] as? PyConstantValue.Str)?.value ?: return null
    val new = (arguments[1] as? PyConstantValue.Str)?.value ?: return null
    val count = arguments.getOrNull(2)?.let { PyConstantFolder.asInteger(it) ?: return null } ?: BigInteger.ONE.negate()
    val limit = if (count.signum() < 0 || count > BigInteger.valueOf(Int.MAX_VALUE.toLong())) Int.MAX_VALUE else count.toInt()
    val parts = if (old.isEmpty()) {
      val codePoints = receiver.codePoints().toArray()
      listOf("") + codePoints.map { String(Character.toChars(it)) } + listOf("")
    }
    else {
      receiver.split(old)
    }
    val result = StringBuilder()
    for ((index, part) in parts.withIndex()) {
      if (index > 0) {
        result.append(if (index <= limit) new else old)
      }
      result.append(part)
      if (result.length > PyConstantFolder.MAX_STRING_LENGTH) {
        return null
      }
    }
    return PyConstantValue.Str(result.toString())
  }
}
