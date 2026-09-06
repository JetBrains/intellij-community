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
 * The offered options follow the type of the formatted value, both right after the colon and after a
 * partly typed spec.
 */
@TestFor(classes = [PyFStringFormatSpecCompletionContributor::class], issues = ["PY-88388", "PY-88389"])
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
    // !s/!r/!a convert the value to a string, so only string-valid specs are offered (no numeric types).
    doTest("f'{42!s:<caret>}'", "s", "<", ">", "^")

  @Test
  fun `completions for bool follow its numeric base`() {
    // A bool is an int subclass, so the numeric options apply to it through its ancestors.
    val variants = complete("""
      def test(flag: bool):
          f"{flag:<caret>}"
    """)
    assertContainsElements(variants, "d", "b", "x", "X", "+")
    assertDoesntContain(variants, "s")
  }

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
  fun `completions after partial format spec`() {
    // A float is numeric, so the string presentation type is not offered after the precision.
    val variants = complete("f'{3.14:.2<caret>}'")
    assertContainsElements(variants, "f", "e", "g", "%")
    assertDoesntContain(variants, "s")
  }

  @Test
  fun `completions after partial format spec with width`() =
    doTest("f'{3.14:r>3<caret>}'", "f")

  @Test
  fun `completions after fill char that is a type letter`() =
    // The 'f' is a fill character (followed by align '>'), not a presentation type, so types are still offered.
    doTest("f'{42:f>5<caret>}'", "d", "e", "g")

  @Test
  fun `partial completions for string exclude the numeric types`() {
    // Only the string presentation type applies after the precision, so completion inserts it directly.
    runInEdtAndWait {
      myFixture.configureByText("test.py", """
        def test(s: str):
            f"{s:.2<caret>}"
      """.trimIndent())
      myFixture.completeBasic()
      myFixture.checkResult("""
        def test(s: str):
            f"{s:.2s}"
      """.trimIndent())
    }
  }

  @Test
  fun `partial completions offer the percentage type`() =
    // '%' is a presentation type, so it belongs to the options offered after a precision.
    doTest("f'{0.25:.2<caret>}'", "%")

  @Test
  fun `partial completions for datetime offer the directives`() =
    doTest("""
      from datetime import datetime
      def test(dt: datetime):
          f"{dt:%Y-<caret>}"
    """, "%m", "%d", "%H")

  @Test
  fun `partial completions for datetime after a typed percent`() =
    doTest("""
      from datetime import datetime
      def test(dt: datetime):
          f"{dt:%<caret>}"
    """, "%Y", "%m")

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
    // The nested field is not spec text, so the 'd' in its name is not an already-typed type.
    doTest("""
      def test(x: float, wd: int):
          f"{x:{wd}.2<caret>}"
    """, "f", "e", "g")

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
