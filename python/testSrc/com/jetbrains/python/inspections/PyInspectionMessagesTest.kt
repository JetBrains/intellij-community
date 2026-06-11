// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestFor(classes = [PyInspectionMessages::class])
class PyInspectionMessagesTest {

  @Test
  fun `backticks become quotes in description and code blocks in tooltip`() {
    val message = PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", "int", "str")

    assertEquals("Expected type 'int', got 'str' instead", message.description)
    assertEquals("<html>Expected type <code>int</code>, got <code>str</code> instead</html>", message.tooltip)
  }

  @Test
  fun `parameter values are html-escaped only in the tooltip`() {
    val message = PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", "A<B", "C&D")

    // Description is plain text: the raw characters are kept verbatim.
    assertEquals("Expected type 'A<B', got 'C&D' instead", message.description)
    // Tooltip is HTML: the same characters are escaped so they cannot break the markup.
    assertEquals("<html>Expected type <code>A&lt;B</code>, got <code>C&amp;D</code> instead</html>", message.tooltip)
  }

  @Test
  fun `template without backticks produces identical plain description and tooltip`() {
    val message = PyPsiBundle.problemMessage("INSP.type.checker.init.should.return.none")

    assertEquals("__init__ should return None", message.description)
    // No code-rendered spans anywhere: the tooltip is the same plain text, with no <html> wrapper.
    assertSame(message.description, message.tooltip)
    assertFalse(message.tooltip.contains("<html>"))
  }

  @Test
  fun `doubled apostrophes in the template survive in both forms`() {
    // Template: Type `{0}` doesn''t have expected {1,choice,1#attribute|2#attributes} {2}
    val message = PyPsiBundle.problemMessage("INSP.type.checker.type.does.not.have.expected.attribute",
                                             "Foo", 2, CodifiedParam.joinNames(listOf("bar", "baz")))

    assertEquals("Type 'Foo' doesn't have expected attributes 'bar', 'baz'", message.description)
    assertEquals("<html>Type <code>Foo</code> doesn't have expected attributes <code>bar</code>, <code>baz</code></html>",
                 message.tooltip)
  }

  @Test
  fun `choice format picks the singular branch`() {
    // Template: TypedDict `{0}` has missing {1,choice,1#key|2#keys}: {2}
    val message = PyPsiBundle.problemMessage("INSP.type.checker.typed.dict.missing.keys",
                                             "Movie", 1, CodifiedParam.joinNames(listOf("year")))

    assertEquals("TypedDict 'Movie' has missing key: 'year'", message.description)
    assertEquals("<html>TypedDict <code>Movie</code> has missing key: <code>year</code></html>", message.tooltip)
  }

  @Test
  fun `choice format picks the plural branch`() {
    val message = PyPsiBundle.problemMessage("INSP.type.checker.typed.dict.missing.keys",
                                             "Movie", 2, CodifiedParam.joinNames(listOf("year", "name")))

    assertEquals("TypedDict 'Movie' has missing keys: 'year', 'name'", message.description)
    assertEquals("<html>TypedDict <code>Movie</code> has missing keys: <code>year</code>, <code>name</code></html>",
                 message.tooltip)
  }

  @Test
  fun `quotes around literal spans are preserved`() {
    // Regression guard: the literal `async for` span used to lose its quotes because the old template wrapped it in
    // single quotes that MessageFormat consumed as an escape. Backticks render the quotes reliably now.
    val message = PyPsiBundle.problemMessage("INSP.type.checker.yield.from.async.generator", "AsyncGenerator[int, None]")

    assertEquals("Cannot yield from 'AsyncGenerator[int, None]', use 'async for' instead", message.description)
    assertEquals("<html>Cannot yield from <code>AsyncGenerator[int, None]</code>, use <code>async for</code> instead</html>",
                 message.tooltip)
  }

  @Test
  fun `joinNames renders each name as a quoted span and an escaped code block`() {
    val joined = CodifiedParam.joinNames(listOf("a<b", "c"))

    assertEquals("'a<b', 'c'", joined.description)
    assertEquals("<code>a&lt;b</code>, <code>c</code>", joined.tooltip)
  }

  @Test
  fun `a doubled backtick is an escape for a literal backtick alongside a code span`() {
    val message = PyInspectionMessages.formatTemplate("a `` b `{0}` c", "X")

    assertEquals("a ` b 'X' c", message.description)
    assertEquals("<html>a ` b <code>X</code> c</html>", message.tooltip)
  }

  @Test
  fun `a doubled backtick is an escape for a literal backtick without any code span`() {
    val message = PyInspectionMessages.formatTemplate("literal `` here")

    assertEquals("literal ` here", message.description)
    assertEquals("<html>literal ` here</html>", message.tooltip)
  }
}
