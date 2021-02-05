// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.markdown

import org.junit.Test
import kotlin.test.assertEquals

internal class MarkdownConverterTest {
  @Test
  fun `multiline text`() {
    val markdownText =
      """
      line1
      line2
      line3
    """.trimIndent()

    val htmlText = "<p>line1<br />line2<br />line3</p>"

    markdownText shouldBe htmlText
  }

  @Test
  fun bold() {
    "**bold**" shouldBe "<p><b>bold</b></p>"
  }

  @Test
  fun italic() {
    "*italic*" shouldBe "<p><i>italic</i></p>"
  }

  @Test
  fun link() {
    "[hello](https://hello.word.com)" shouldBe """<p><a href="https://hello.word.com">hello</a></p>"""
  }

  @Test
  fun `inline code block`() {
    "text before `code` text after" shouldBe "<p>text before <code>code</code> text after</p>"
  }

  @Test
  fun `multiline code block`() {
    val markdownText =
      """
      text before
      ```
      begin
      its code block
      end
      ```
      text after
    """.trimIndent()

    val htmlText = """
      <p>text before</p><pre><code>begin
      its code block
      end
      </code></pre><p>text after</p>
    """.trimIndent()

    markdownText shouldBe htmlText
  }

  @Test
  fun `inline link`() {
    "https://hello.world.com" shouldBe """<p><a href="https://hello.world.com">https://hello.world.com</a></p>"""
  }

  @Test
  fun `relative link`() {
    val markdownText = "[1 commit](/p/pr)"
    val htmlText = """<p><a href="https://jetbrains.com/p/pr">1 commit</a></p>"""
    assertEquals(htmlText, convertToHtml(markdownText, "https://jetbrains.com"))
  }

  @Test
  fun strikethrough() {
    "~~removed~~" shouldBe "<p><strike>removed</strike></p>"
  }


  private infix fun String.shouldBe(htmlText: String) {
    assertEquals(htmlText, convertToHtml(this))
  }
}