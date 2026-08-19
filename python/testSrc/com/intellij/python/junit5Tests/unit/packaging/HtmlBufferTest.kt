// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.jetbrains.python.requirements.HtmlBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PY-91067: the Quick Doc tooltip for `requirements.txt` used to render the accumulator's
 * `Object.toString()` identity (`HtmlBuffer@57f54152`) because `HtmlBuffer` never overrode
 * `toString()`. These tests pin the accumulator's behavior: `toString` must return the
 * accumulated HTML, `text` must escape user-supplied strings, `raw` must emit verbatim, and
 * the two paths must compose in call order.
 */
internal class HtmlBufferTest {

  @Test
  fun `toString returns accumulated content, not the Object identity`() {
    val buffer = HtmlBuffer().text("hello").raw("<br>").text("world")

    val html = buffer.toString()

    assertEquals("hello<br>world", html)
    // Guard against the regression itself — the identity string looks like `HtmlBuffer@57f54152`.
    assertFalse(
      html.startsWith("com.jetbrains.python.requirements.HtmlBuffer@")
      || html.startsWith("HtmlBuffer@"),
      "toString must not return the default Object identity string; got `$html`",
    )
  }

  @Test
  fun `toString on an empty buffer returns an empty string`() {
    assertEquals("", HtmlBuffer().toString())
  }

  @Test
  fun `text escapes XML special characters`() {
    val html = HtmlBuffer()
      .text("<script>alert('x')</script>")
      .toString()

    assertEquals("&lt;script&gt;alert('x')&lt;/script&gt;", html)
  }

  @Test
  fun `text escapes an ampersand exactly once`() {
    val html = HtmlBuffer().text("Rock & Roll").toString()

    assertEquals("Rock &amp; Roll", html)
    assertFalse(html.contains("&amp;amp;"), "double-escape regression check")
  }

  @Test
  fun `raw preserves markup verbatim`() {
    val html = HtmlBuffer().raw("<b>bold</b>").toString()

    assertEquals("<b>bold</b>", html)
  }

  @Test
  fun `text and raw compose in call order`() {
    val name = "<foo & bar>"
    val html = HtmlBuffer()
      .raw("<html>")
      .text(name)
      .raw("</html>")
      .toString()

    assertEquals("<html>&lt;foo &amp; bar&gt;</html>", html)
    assertTrue(html.startsWith("<html>"), "raw prefix survives verbatim")
    assertTrue(html.endsWith("</html>"), "raw suffix survives verbatim")
  }

  @Test
  fun `builder methods return the same instance for chaining`() {
    val buffer = HtmlBuffer()

    assertTrue(buffer.text("a") === buffer, "text must return the receiver for fluent chaining")
    assertTrue(buffer.raw("b") === buffer, "raw must return the receiver for fluent chaining")
  }
}
