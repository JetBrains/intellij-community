// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.fixtures.PyTestCase

/**
 * Tests for f-string format specification syntax highlighting (PY-88215).
 *
 * Tests that format spec components (dots, numbers, format type characters) are highlighted
 * only when the formatted expression is a numeric type.
 */
class PyFStringFormatSpecHighlightingTest : PyTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = ourPyLatestDescriptor

  fun `test float literal format spec`() =
    // Numeric literal - should highlight: . (special) (number) f (special)
    doTest("f'{.1:$dot$number$f_type}'")

  fun `test int literal format spec`() =
    // Integer literal - should highlight: 0 (special flag) (number) d (special)
    doTest("f'{1:$zero$number$d_type}'")

  fun `test complex literal format spec`() =
    // complex literal - should highlight: 0 (special flag) (number) d (special)
    doTest("f'{1j:$zero$number$d_type}'")

  fun `test string literal format spec`() =
    // String literal - should highlight: > (special (numbers)
    doTest("f'{'':$align$number}'")

  fun `test typed float variable`() =
    // Variable with float type annotation - should highlight
    doTest("""
      def f(x: float):
          s = f"{x:$dot$number$f_type}"
    """, extra=false)

  fun `test typed int variable`() =
    // Variable with int type annotation - should highlight
    doTest("""
      def f(n: int):
          s = f"{n:$number$number$d_type}"
    """, extra=false)

  fun `test typed string variable`() =
    // Variable with str type annotation - SHOULD highlight string format components
    doTest("""
      def f(s: str):
          x = f"{s:$align$number}"
    """, extra=false)

  fun `test untyped variable`() =
    // Variable without type annotation - should NOT highlight (unknown type)
    doTest("f'{x:.2f}'")

  fun `test None typed variable`() =
    // optional int, while it can cause an error, still highlight
    doTest("""
      def f(x: int | None):
        f'{x:$dot$number$f_type}'
      """, extra=false)

  fun `test complex format spec`() =
    // Complex format spec: + (special sign) 0 (special flag) (numbers) . (special) (numbers) f (special)
    doTest("f'{.1:$plus$zero$number$dot$number$f_type}'")

  fun `test scientific notation`() =
    // Scientific notation format: . (special) 3 (number) e (special)
    doTest("f'{.1:$dot$number$e_type}'")

  fun `test hex format`() =
    // Hex format: 0 (special flag) 8 (number) x (special)
    doTest("f'{1:$zero$number$x_type}'")

  fun `test percent format`() =
    // Percent format: . (special) 1 (number) % (special)
    doTest("f'{.1:$dot$number$percent_type}'")

  fun `test numeric with fill and alignment`() =
    // In "s>10", 's' is fill (NOT highlighted), '>' is alignment (special), '1' '0' are width (numbers)
    doTest("f'{1:s$align$number}'")

  fun `test union type int or str`() =
    // Union type - treated as numeric, '>' is alignment, '1' '0' are width
    doTest("""
      def f(a: int | str):
          print(f"{a:$align$number}")
    """, extra=false)

  fun `test datetime`() =
    // datetime with strftime format codes - should highlight %Y, %m, %d as special
    doTest("""
      from datetime import datetime
      x = f"{datetime.<info>now</info>():$percent_Y-$percent_m-$percent_d}"
    """)

  fun `test datetime with time format`() =
    // datetime with time format codes
    doTest("""
      from datetime import datetime
      x = f"{datetime.now():$percent_H:$percent_M:$percent_S}"
    """, extra = false)

  fun `test date object format`() =
    // date object with strftime codes
    doTest("""
      from datetime import date
      f"{date.today():$percent_A, $percent_B $percent_d, $percent_Y}"
    """, extra=false)

  fun `test bytes not highlighted`() =
    // bytes should NOT be highlighted
    doTest("f'{b'':>10}'")

  fun `test conversion modifier str`() =
    // !s converts to str
    doTest("f'{1!s:$string_type}'")

  fun `test conversion modifier repr`() =
    // !r converts to str, so '>' and '1' '0' should be highlighted
    doTest("f'{1!r:$align$number}'")

  fun `test conversion modifier ascii`() =
    // !a converts to str, so '<' '2' '0' 's' should be highlighted
    doTest("f'{1!a:$left_align$number$number$string_type}'")

  fun `test conversion modifier with numeric original type`() =
    // Even though original is float, !s converts to str
    doTest("f'{.1!s:$center_align$number$number}'")

  fun `test decimal type`() =
    // Decimal should be highlighted as numeric type
    doTest("""
      from decimal import Decimal
      x = f"{Decimal("123.45"):$dot$number$f_type}"
    """, extra=false)

  fun `test sign plus`() =
    // Test + sign character (special) and d (special)
    doTest("f'{1:$plus$d_type}'")

  fun `test sign minus`() =
    // Test - sign character (special) and d (special)
    doTest("f'{1:$minus$d_type}'")

  fun `test sign space`() =
    // Test space sign character (special) and d (special)
    doTest("f'{1:$space_sign$d_type}'")

  fun `test z flag`() =
    // Test z flag (special), 5 (number), d (special)
    doTest("f'{-1:$z_flag$number$d_type}'")

  fun `test hash flag`() =
    // Test # flag (special) and x (special)
    doTest("f'{255:$hash$x_type}'")

  fun `test zero flag`() =
    // Test 0 flag (special), 5 (number), d (special)
    doTest("f'{1:$zero$number$d_type}'")

  fun `test not zero flag`() =
    // Test "1010" (numbers), d (special)
    doTest("f'{1:$number$number$d_type}'")

  fun `test zero flag with zero in width`() =
    // "0010d": first 0 is flag (special), 010 is width (numbers) - second 0 must NOT be treated as another flag
    doTest("f'{42:${zero}$numberZero$number${d_type}}'", extra = false)

  fun `test comma grouping`() =
    // Test comma grouping separator (special) and d (special)
    doTest("f'{1:$comma$d_type}'")

  fun `test underscore grouping`() =
    // Test underscore grouping separator (special) and d (special)
    doTest("f'{1:$underscore$d_type}'")

  fun `test comma grouping with width`() =
    // Test width (1 5 numbers), comma (special), d (special)
    doTest("f'{1:$number$comma$d_type}'")

  fun `test precision with grouping`() =
    // Test . (special), 2 (number), _ (special grouping), f (special)
    doTest("f'{.1:$dot$number$underscore$f_type}'")


  fun `test complex format with all options`() =
  // Test format with all options: 0 (fill char, NOT highlighted), > (special align), + (special sign),
    // # (special flag), 0 (special flag), 1 5 (numbers), , (special grouping), . (special), 2 (number), f (special)
    doTest("f'{1:0$align$plus$hash$zero$number$comma$dot$number$f_type}'")

  fun `test s type for numeric`() =
    // Test 's' type for numeric, it should highlight as special, we will show an error elsewhere
    doTest("f'{1:$string_type}'")

  fun `test plus as fill character`() =
    // In "+>10", '+' is fill (NOT highlighted), '>' is alignment (special), '1' '0' are width (numbers)
    doTest("f'{1:+$align$number}'")

  private fun doTest(text: String, extra: Boolean = true) {
    myFixture.configureByText("test.py", text.trimIndent())
    myFixture.checkHighlighting(false, true, false, extra.not())
  }
}

// Helper function for special character highlighting
private fun special(char: Char) = """<info textAttributesKey="PY.FSTRING_FORMAT_SPEC_SPECIAL_CHAR">$char</info>"""
private fun special(str: String) = """<info textAttributesKey="PY.FSTRING_FORMAT_SPEC_SPECIAL_CHAR">$str</info>"""

private const val numberZero =
  """<info textAttributesKey="PY.FSTRING_FORMAT_SPEC_NUMBER">0</info>"""
private const val number =
  """<info textAttributesKey="PY.FSTRING_FORMAT_SPEC_NUMBER">1</info>$numberZero"""

// Alignment characters
private val align = special('>')
private val left_align = special('<')
private val center_align = special('^')
private val equal_align = special('=')

// Special characters
private val dot = special('.')
private val comma = special(',')
private val underscore = special('_')

// Format type characters
private val string_type = special('s')
private val d_type = special('d')
private val f_type = special('f')
private val e_type = special('e')
private val x_type = special('x')
private val percent_type = special('%')

// Sign characters
private val plus = special('+')
private val minus = special('-')
private val space_sign = special(' ')

// Flag characters
private val zero = special('0')
private val hash = special('#')
private val z_flag = special('z')

// Strftime format codes (for datetime)
private val percent_Y = special("%Y")
private val percent_m = special("%m")
private val percent_d = special("%d")
private val percent_H = special("%H")
private val percent_M = special("%M")
private val percent_S = special("%S")
private val percent_A = special("%A")
private val percent_B = special("%B")
