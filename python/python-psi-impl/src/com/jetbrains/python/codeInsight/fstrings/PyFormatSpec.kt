// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.fstrings

/**
 * The single parser of the Python format mini-language.
 *
 * Every consumer reads a spec through [PyFormatSpec.parse], so the annotator, the completion contributor
 * and the documentation providers agree on what a character means. Each consumer still applies its own
 * policy on top. The annotator highlights whatever the syntax allows, and completion offers only what the
 * value type accepts.
 */

/** The kind of one recognized piece of a format spec. */
enum class PyFormatSpecComponentKind {
  /** The padding character of a `[fill]align` pair. It carries no meaning of its own. */
  FILL,
  ALIGN,
  SIGN,
  COERCE_NEGATIVE_ZERO,
  ALTERNATE_FORM,
  ZERO_PADDING,
  /** A run of digits that gives the minimum field width. */
  WIDTH,
  GROUPING,
  /** The `.` that starts the precision. */
  PRECISION_MARK,
  /** The run of digits after [PRECISION_MARK]. */
  PRECISION,
  /** The presentation type that ends the spec. */
  TYPE,
  /** A `%` directive of a datetime spec, such as `%Y`. */
  DATETIME_DIRECTIVE,
}

/** One recognized piece of a format spec, with its offset inside the parsed text. */
class PyFormatSpecComponent(
  val kind: PyFormatSpecComponentKind,
  val text: String,
  val startOffset: Int,
) {
  val endOffset: Int get() = startOffset + text.length

  override fun toString(): String = "$kind('$text'@$startOffset)"
}

/**
 * A parsed format spec.
 *
 * The scan is lenient on purpose. It classifies each character it recognizes wherever it appears, instead
 * of rejecting a spec whose components are out of order, because a partly typed or an invalid spec still
 * needs highlighting and documentation.
 */
class PyFormatSpec private constructor(
  val text: String,
  val components: List<PyFormatSpecComponent>,
) {
  /** The first component of [kind], or `null` when the spec has none. */
  fun first(kind: PyFormatSpecComponentKind): PyFormatSpecComponent? = components.firstOrNull { it.kind == kind }

  fun has(kind: PyFormatSpecComponentKind): Boolean = components.any { it.kind == kind }

  val isDatetime: Boolean get() = has(PyFormatSpecComponentKind.DATETIME_DIRECTIVE)

  val datetimeDirectives: List<String>
    get() = components.filter { it.kind == PyFormatSpecComponentKind.DATETIME_DIRECTIVE }.map { it.text }

  /** True when the spec already carries a presentation type, so nothing more can follow it. */
  val hasPresentationType: Boolean get() = has(PyFormatSpecComponentKind.TYPE)

  /** The padding character of a `[fill]align` pair. */
  val fill: Char? get() = charOf(PyFormatSpecComponentKind.FILL)
  val align: Char? get() = charOf(PyFormatSpecComponentKind.ALIGN)
  val sign: Char? get() = charOf(PyFormatSpecComponentKind.SIGN)
  val grouping: Char? get() = charOf(PyFormatSpecComponentKind.GROUPING)
  val type: Char? get() = charOf(PyFormatSpecComponentKind.TYPE)

  val hasCoerceNegativeZero: Boolean get() = has(PyFormatSpecComponentKind.COERCE_NEGATIVE_ZERO)
  val hasAlternateForm: Boolean get() = has(PyFormatSpecComponentKind.ALTERNATE_FORM)
  val hasZeroPadding: Boolean get() = has(PyFormatSpecComponentKind.ZERO_PADDING)

  /** The digits of the minimum field width. */
  val width: String? get() = first(PyFormatSpecComponentKind.WIDTH)?.text

  /**
   * The digits after the `.`, an empty string when the spec carries a bare `.`, or `null` when it states
   * no precision at all.
   */
  val precision: String?
    get() = if (has(PyFormatSpecComponentKind.PRECISION_MARK)) first(PyFormatSpecComponentKind.PRECISION)?.text ?: "" else null

  /** True when the scan recognized nothing at all, so there is nothing to describe. */
  val isEmpty: Boolean get() = components.isEmpty()

  private fun charOf(kind: PyFormatSpecComponentKind): Char? = first(kind)?.text?.firstOrNull()

  companion object {
    /** A datetime directive is `%` and a letter (e.g. `%Y`) or `%%`. A lone or trailing `%` is the percentage type. */
    private val DATETIME_DIRECTIVE = Regex("%[A-Za-z%]")

    const val ALIGN_CHARS: String = "<>=^"
    const val SIGN_CHARS: String = "+- "
    const val GROUPING_CHARS: String = ",_"
    const val PRESENTATION_TYPE_CHARS: String = "bcdeEfFgGnosxX%"

    /** True when [text] reads as a datetime spec rather than a standard one. */
    @JvmStatic
    fun looksLikeDatetime(text: String): Boolean = DATETIME_DIRECTIVE.containsMatchIn(text)

    /**
     * Parses [text], the spec that follows the colon.
     *
     * Pass `datetime = true` to read the text as a strftime string. A caller that knows the value type
     * decides from the type, and one that does not uses [looksLikeDatetime].
     */
    @JvmStatic
    fun parse(text: String, datetime: Boolean = looksLikeDatetime(text)): PyFormatSpec =
      PyFormatSpec(text, if (datetime) parseDatetime(text) else parseStandard(text))

    private fun parseDatetime(text: String): List<PyFormatSpecComponent> {
      val components = mutableListOf<PyFormatSpecComponent>()
      var i = 0
      while (i < text.length) {
        if (text[i] == '%' && i + 1 < text.length) {
          components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.DATETIME_DIRECTIVE, text.substring(i, i + 2), i))
          i += 2
        }
        else {
          i++
        }
      }
      return components
    }

    private fun parseStandard(text: String): List<PyFormatSpecComponent> {
      val components = mutableListOf<PyFormatSpecComponent>()

      // A fill character is only a fill when an alignment operator follows it, so a fill that happens to be
      // a presentation-type letter (the 'f' in "f>5") is not read as a type.
      var i = 0
      if (text.length > 1 && text[1] in ALIGN_CHARS) {
        components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.FILL, text.substring(0, 1), 0))
        components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.ALIGN, text.substring(1, 2), 1))
        i = 2
      }

      var seenZeroPadding = false
      var seenDigit = false
      var seenPrecisionMark = false

      while (i < text.length) {
        val char = text[i]
        when {
          char in ALIGN_CHARS -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.ALIGN, char.toString(), i))
            i++
          }
          char in SIGN_CHARS -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.SIGN, char.toString(), i))
            i++
          }
          char == 'z' -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.COERCE_NEGATIVE_ZERO, char.toString(), i))
            i++
          }
          char == '#' -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.ALTERNATE_FORM, char.toString(), i))
            i++
          }
          // A leading '0' is the sign-aware zero-padding flag only when a width follows it.
          char == '0' && !seenZeroPadding && !seenDigit && text.getOrNull(i + 1)?.isDigit() == true -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.ZERO_PADDING, char.toString(), i))
            seenZeroPadding = true
            i++
          }
          char.isDigit() -> {
            val end = digitRunEnd(text, i)
            val kind = if (seenPrecisionMark) PyFormatSpecComponentKind.PRECISION else PyFormatSpecComponentKind.WIDTH
            components.add(PyFormatSpecComponent(kind, text.substring(i, end), i))
            seenDigit = true
            i = end
          }
          char in GROUPING_CHARS -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.GROUPING, char.toString(), i))
            i++
          }
          char == '.' -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.PRECISION_MARK, char.toString(), i))
            seenPrecisionMark = true
            i++
          }
          // A presentation type ends the spec, so it only counts at the end of the text.
          char in PRESENTATION_TYPE_CHARS && isAtSpecEnd(text, i) -> {
            components.add(PyFormatSpecComponent(PyFormatSpecComponentKind.TYPE, char.toString(), i))
            i++
          }
          else -> i++
        }
      }

      return components
    }

    private fun digitRunEnd(text: String, start: Int): Int {
      var end = start
      while (end < text.length && text[end].isDigit()) end++
      return end
    }

    private fun isAtSpecEnd(text: String, index: Int): Boolean =
      index == text.lastIndex || text[index + 1] in " \t}"
  }
}
