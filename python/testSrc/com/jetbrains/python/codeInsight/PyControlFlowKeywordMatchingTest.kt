// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.codeInsight.highlighting.HeavyBraceHighlighter
import com.intellij.idea.TestFor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-53379"])
class PyControlFlowKeywordMatchingTest : PyCodeInsightTestCase() {

  // region off-screen header preview (HeavyBraceHighlighter)

  @Test
  fun `else matches enclosing if`() = assertMatch("""
    if a:
        f()
    el<caret>se:
        g()
  """, header = "if", current = "else")

  @Test
  fun `elif matches enclosing if`() = assertMatch("""
    if a:
        f()
    el<caret>if b:
        g()
  """, header = "if", current = "elif")

  @Test
  fun `second except matches enclosing try`() = assertMatch("""
    try:
        f()
    except A:
        g()
    exc<caret>ept B:
        h()
  """, header = "try", current = "except")

  @Test
  fun `finally matches enclosing try`() = assertMatch("""
    try:
        f()
    except A:
        g()
    fin<caret>ally:
        h()
  """, header = "try", current = "finally")

  @Test
  fun `for-else matches enclosing for`() = assertMatch("""
    for x in xs:
        f(x)
    el<caret>se:
        g()
  """, header = "for", current = "else")

  @Test
  fun `async for-else matches enclosing async for`() = assertMatch("""
    async def f():
        async for x in xs:
            g(x)
        el<caret>se:
            h()
  """, header = "for", current = "else")

  @Test
  fun `case matches enclosing match`() {
    assertMatch("""
      match command:
          case "north":
              go_north()
          ca<caret>se "south":
              go_south()
    """, header = "match", current = "case")
  }

  @Test
  fun `caret right after keyword still matches`() = assertMatch("""
    if a:
        f()
    else<caret>:
        g()
  """, header = "if", current = "else")

  @Test
  fun `header keyword has no preview`() = assertNoMatch("""
    i<caret>f a:
        f()
    else:
        g()
  """)

  @Test
  fun `ternary else is ignored`() = assertNoMatch("""
    x = a if cond el<caret>se b
  """)

  @Test
  fun `comprehension if is ignored`() = assertNoMatch("""
    xs = [y for y in ys i<caret>f y]
  """)

  @Test
  fun `return matches enclosing def`() = assertMatch("""
    def f(x):
        if x:
            ret<caret>urn 1
        return 2
  """, header = "def", current = "return")

  @Test
  fun `yield matches enclosing def`() = assertMatch("""
    def gen():
        yi<caret>eld 1
  """, header = "def", current = "yield")

  @Test
  fun `async def is matched`() = assertMatch("""
    async def f():
        ret<caret>urn 1
  """, header = "def", current = "return")

  @Test
  fun `def keyword has no preview`() = assertNoMatch("""
    de<caret>f f():
        return 1
  """)

  @Test
  fun `return matches nearest enclosing def not outer`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      def outer():
          def inner():
              ret<caret>urn 1
          return 2
    """.trimIndent())
    val match = HeavyBraceHighlighter.match(myFixture.file, myFixture.caretOffset)
    assertNotNull(match)
    assertEquals(myFixture.file.text.indexOf("def inner"), match!!.first.startOffset)
  }

  @Test
  fun `raise matches enclosing def`() = assertMatch("""
    def f(x):
        if x:
            ra<caret>ise ValueError
        return 1
  """, header = "def", current = "raise")

  @Test
  fun `break matches enclosing for`() = assertMatch("""
    for x in xs:
        if x:
            bre<caret>ak
  """, header = "for", current = "break")

  @Test
  fun `continue matches enclosing while`() = assertMatch("""
    while cond:
        if x:
            cont<caret>inue
  """, header = "while", current = "continue")

  @Test
  fun `break matches nearest enclosing loop not outer`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      for x in xs:
          for y in ys:
              bre<caret>ak
    """.trimIndent())
    val match = HeavyBraceHighlighter.match(myFixture.file, myFixture.caretOffset)
    assertNotNull(match)
    assertEquals(myFixture.file.text.indexOf("for y"), match!!.first.startOffset)
  }

  // endregion

  // region matching-keyword highlighting and navigation (CodeBlockSupportHandler)

  @Test
  fun `else marks the if and else`() = assertMarkers("""
    if a:
        f()
    el<caret>se:
        g()
  """, "if", "else")

  @Test
  fun `elif marks all parts of the if`() = assertMarkers("""
    i<caret>f a:
        f()
    elif b:
        g()
    else:
        h()
  """, "if", "elif", "else")

  @Test
  fun `finally marks all parts of the try`() = assertMarkers("""
    try:
        f()
    except A:
        g()
    fin<caret>ally:
        h()
  """, "try", "except", "finally")

  @Test
  fun `case marks the match and all cases`() {
    assertMarkers("""
      match command:
          ca<caret>se "north":
              go_north()
          case "south":
              go_south()
    """, "match", "case", "case")
  }

  @Test
  fun `code block range spans the whole if statement`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      if a:
          f()
      el<caret>se:
          g()
    """.trimIndent())
    val range = CodeBlockSupportHandler.findCodeBlockRange(myFixture.editor, myFixture.file)
    assertEquals(myFixture.file.text, textOf(range))
  }

  @Test
  fun `exit keywords are not code block markers`() = assertNoMarkers("""
    def f():
        ret<caret>urn 1
  """)

  @Test
  fun `break is not a code block marker`() = assertNoMarkers("""
    for x in xs:
        bre<caret>ak
  """)

  @Test
  fun `ternary else is not a code block marker`() = assertNoMarkers("""
    x = a if cond el<caret>se b
  """)

  // endregion

  private fun assertMatch(code: String, header: String, current: String) = runInEdtAndWait {
    myFixture.configureByText("a.py", code.trimIndent())
    val match = HeavyBraceHighlighter.match(myFixture.file, myFixture.caretOffset)
    assertNotNull(match, "Expected a control-flow keyword match at the caret")
    assertEquals(header, textOf(match!!.first))
    assertEquals(current, textOf(match.second))
  }

  private fun assertNoMatch(code: String) = runInEdtAndWait {
    myFixture.configureByText("a.py", code.trimIndent())
    assertNull(HeavyBraceHighlighter.match(myFixture.file, myFixture.caretOffset))
  }

  private fun assertMarkers(code: String, vararg expected: String) = runInEdtAndWait {
    myFixture.configureByText("a.py", code.trimIndent())
    val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
    val ranges = CodeBlockSupportHandler.findMarkersRanges(element)
    assertEquals(expected.toList(), ranges.map { textOf(it) })
  }

  private fun assertNoMarkers(code: String) = runInEdtAndWait {
    myFixture.configureByText("a.py", code.trimIndent())
    val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return@runInEdtAndWait
    assertTrue(CodeBlockSupportHandler.findMarkersRanges(element).isEmpty())
  }

  private fun textOf(range: TextRange): String = myFixture.file.text.substring(range.startOffset, range.endOffset)
}
