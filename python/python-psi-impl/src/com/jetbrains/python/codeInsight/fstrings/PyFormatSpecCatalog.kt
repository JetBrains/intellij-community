// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.fstrings

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.ALIGNMENT
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.DATETIME
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.FLAG
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.GROUPING
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.PRECISION
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.SIGN
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory.TYPE
import org.jetbrains.annotations.NonNls

/**
 * Every option of the format mini-language, with its documentation and its canonical example.
 *
 * This is the one source the annotator, the completion contributor and the documentation providers read,
 * so an option is described in exactly one place.
 */
object PyFormatSpecCatalog {

  /**
   * Builds an option whose descriptions resolve from `PyPsiBundle` under [id]: the keys
   * `fstring.format.spec.entry.<id>.short` and `fstring.format.spec.entry.<id>.full`.
   */
  private fun option(spec: String, id: String, category: PyFormatSpecCategory, example: @NonNls String? = null): PyFormatSpecOption =
    PyFormatSpecOption(
      spec = spec,
      category = category,
      shortDescriptionSupplier = PyPsiBundle.messagePointer("fstring.format.spec.entry.$id.short"),
      fullDescriptionSupplier = PyPsiBundle.messagePointer("fstring.format.spec.entry.$id.full"),
      example = example,
    )

  /** Every option, in the order the completion list shows them. */
  val options: List<PyFormatSpecOption> = listOf(
    // Alignment options
    option("<", "left", ALIGNMENT, "f\"{text:<10}\"  # 'hello     '"),
    option(">", "right", ALIGNMENT, "f\"{text:>10}\"  # '     hello'"),
    option("^", "center", ALIGNMENT, "f\"{text:^10}\"  # '  hello   '"),
    option("=", "pad.after.sign", ALIGNMENT, "f\"{num:=+10}\"  # '+      42'"),

    // Sign options
    option("+", "sign.both", SIGN, "f\"{42:+}\"      # '+42'"),
    option("-", "sign.negative", SIGN, "f\"{-42:-}\"     # '-42'"),
    option(" ", "sign.space", SIGN, "f\"{42: }\"      # ' 42'"),

    // Special flags
    option("z", "coerce.zero", FLAG),
    option("#", "alternate", FLAG, "f\"{255:#x}\"    # '0xff'"),
    option("0", "zero.padding", FLAG, "f\"{42:05}\"     # '00042'"),

    // Precision
    option(".", "precision", PRECISION, "f\"{3.14159:.2f}\" # '3.14'"),

    // Grouping options
    option(",", "comma", GROUPING, "f\"{1000000:,}\" # '1,000,000'"),
    option("_", "underscore", GROUPING, "f\"{1000000:_}\" # '1_000_000'"),

    // Presentation types
    option("s", "string", TYPE, "f\"{text:s}\"    # 'hello'"),
    option("b", "binary", TYPE, "f\"{42:b}\"      # '101010'"),
    option("c", "character", TYPE, "f\"{65:c}\"      # 'A'"),
    option("d", "decimal", TYPE, "f\"{42:d}\"      # '42'"),
    option("o", "octal", TYPE, "f\"{42:o}\"      # '52'"),
    option("x", "hex.lower", TYPE, "f\"{255:x}\"     # 'ff'"),
    option("X", "hex.upper", TYPE, "f\"{255:X}\"     # 'FF'"),
    option("n", "locale.number", TYPE, "f\"{1234:n}\"    # '1,234' (locale-dependent)"),
    option("e", "scientific.lower", TYPE, "f\"{1234.5:e}\"  # '1.234500e+03'"),
    option("E", "scientific.upper", TYPE, "f\"{1234.5:E}\"  # '1.234500E+03'"),
    option("f", "fixed", TYPE, "f\"{3.14159:f}\" # '3.141590'"),
    option("F", "fixed.upper", TYPE, "f\"{float('nan'):F}\" # 'NAN'"),
    option("g", "general.lower", TYPE, "f\"{0.000123:g}\" # '0.000123'"),
    option("G", "general.upper", TYPE, "f\"{0.000123:G}\" # '0.000123'"),
    option("%", "percentage", TYPE, "f\"{0.25:%}\"    # '25.000000%'"),

    // Datetime format codes
    option("%Y", "year4", DATETIME, "f\"{dt:%Y}\"    # '2024'"),
    option("%y", "year2", DATETIME),
    option("%m", "month", DATETIME, "f\"{dt:%m}\"    # '03'"),
    option("%d", "day", DATETIME, "f\"{dt:%d}\"    # '15'"),
    option("%j", "day.of.year", DATETIME),
    option("%H", "hour24", DATETIME, "f\"{dt:%H}\"    # '14'"),
    option("%I", "hour12", DATETIME),
    option("%M", "minute", DATETIME, "f\"{dt:%M}\"    # '30'"),
    option("%S", "second", DATETIME, "f\"{dt:%S}\"    # '45'"),
    option("%f", "microsecond", DATETIME),
    option("%p", "ampm", DATETIME),
    option("%A", "weekday.full", DATETIME),
    option("%a", "weekday.abbrev", DATETIME),
    option("%w", "weekday.num", DATETIME),
    option("%u", "iso.weekday", DATETIME),
    option("%W", "week.mon", DATETIME),
    option("%U", "week.sun", DATETIME),
    option("%V", "iso.week", DATETIME),
    option("%B", "month.full", DATETIME),
    option("%b", "month.abbrev", DATETIME),
    option("%Z", "tz.name", DATETIME),
    option("%z", "utc.offset", DATETIME),
    option("%c", "datetime.repr", DATETIME),
    option("%x", "date.repr", DATETIME),
    option("%X", "time.repr", DATETIME),
    option("%%", "literal.percent", DATETIME),
  )

  /** The options indexed by their spec string, for a component lookup. */
  val optionsBySpec: Map<String, PyFormatSpecOption> = options.associateBy { it.spec }

  /** The options of [category]. */
  fun of(category: PyFormatSpecCategory): List<PyFormatSpecOption> = options.filter { it.category == category }

  operator fun get(spec: String): PyFormatSpecOption? = optionsBySpec[spec]
}
