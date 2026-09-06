// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.codeInsight.completion.PyFStringFormatSpecCompletionContributor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * Tests for f-string format specification code completion.
 *
 * The offered options follow the type of the formatted value.
 */
@TestFor(classes = [PyFStringFormatSpecCompletionContributor::class], issues = ["PY-88388"])
class PyFStringFormatSpecCompletionTest : PyCodeInsightTestCase() {

  @Test
  fun `basic completions after colon`() =
    doTest("f'{1:<caret>}'", "d", "f", ".", "<", ">", "^", "+", "-", "0", "#", ",", "_")

  @Test
  fun `completions for float variable`() =
    doTest("""
      def test(x: float):
          f"{x:<caret>}"
    """, "f", "e", "E", ".", "d", "<", ">")

  @Test
  fun `completions for int variable`() =
    doTest("""
      def test(n: int):
          f"{n:<caret>}"
    """, "d", "b", "o", "x", "X", "<", ">", "^", "+", "-")

  @Test
  fun `completions for string variable`() =
    doTest("""
      def test(s: str):
          f"{s:<caret>}"
    """, "s", "<", ">", "^")

  @Test
  fun `completions for datetime`() =
    doTest("""
      from datetime import datetime
      def test(dt: datetime):
          f"{dt:<caret>}"
    """, "%Y", "%m", "%d", "%H", "%M", "%S")

  @Test
  fun `datetime offers no standard presentation type`() {
    val variants = complete("""
      from datetime import datetime
      def test(dt: datetime):
          f"{dt:<caret>}"
    """)
    assertDoesntContain(variants, "d", "f", "s")
  }

  @Test
  fun `completions for unknown type`() =
    doTest("f'{unknown_var:<caret>}'", ".", "d", "f", "s", "<", ">")

  @Test
  fun `completions with type conversion`() =
    // !s/!r/!a convert the value to a string, so the string and numeric options apply.
    doTest("f'{42!s:<caret>}'", "s", "<", ">", "^")

  @Test
  fun `completions for bool follow its numeric base`() =
    // A bool is an int subclass, so the mini-language applies to it through its ancestors.
    doTest("""
      def test(flag: bool):
          f"{flag:<caret>}"
    """, "d", "b", "x", "X", "+")

  @Test
  fun `dot completion for precision`() =
    doTest("f'{3.14:<caret>}'", ".")

  @Test
  fun `alignment completions`() =
    doTest("f'{42:<caret>}'", "<", ">", "^", "=")

  @Test
  fun `sign completions for numeric type`() =
    doTest("f'{42:<caret>}'", "+", "-", " ")

  @Test
  fun `format type completions`() =
    doTest("f'{42:<caret>}'", "b", "d", "o", "x", "X", "e", "E", "f", "F", "g", "G", "%")

  @Test
  fun `common completions once the spec carries text`() =
    doTest("f'{3.14:.2<caret>}'", "f", "d")

  @Test
  fun `no format specs inside a nested replacement field`() {
    // The caret is on the nested `width` expression, which has its own completions.
    val variants = complete("""
      def test(x: float, width1: int, width2: int):
          f"{x:{width<caret>}}"
    """)
    assertContainsElements(variants, "width1", "width2")
    assertDoesntContain(variants, ".", "<", ">", "d", "f", "s")
  }

  @Test
  fun `completions after a nested replacement field`() =
    // The nested field is not spec text, so the 'd' in its name does not stop the completion.
    doTest("""
      def test(x: float, wd: int):
          f"{x:{wd}.2<caret>}"
    """, "f", "d")

  private fun doTest(code: String, vararg expected: String) =
    assertContainsElements(complete(code), *expected)

  private fun complete(code: String): List<String> {
    var variants: List<String> = emptyList()
    runInEdtAndWait {
      myFixture.configureByText("test.py", code.trimIndent())
      myFixture.completeBasic()
      variants = myFixture.lookupElementStrings ?: emptyList()
    }
    return variants
  }
}
