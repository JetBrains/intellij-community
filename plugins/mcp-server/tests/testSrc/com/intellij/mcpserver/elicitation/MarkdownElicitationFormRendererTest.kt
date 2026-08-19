package com.intellij.mcpserver.elicitation

import com.intellij.lang.Language
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Code
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Styled
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Text
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.TextColor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [renderToMarkdown]: the `ElicitationMessagePart` -> Markdown converter.
 */
@TestApplication
class MarkdownElicitationFormRendererTest {

  @Test
  fun `text parts are joined in order and newlines become hard breaks`() {
    val out = renderToMarkdown(listOf(Text("first\n"), Text("second\n")))
    assertThat(out).isEqualTo("first  \nsecond  \n")
  }

  @Test
  fun `a newline that already has a hard break is left alone`() {
    assertThat(renderToMarkdown(listOf(Text("first  \nsecond")))).isEqualTo("first  \nsecond")
  }

  @Test
  fun `text with no newlines is copied verbatim and never escaped`() {
    val text = "public.user_accounts * 2 [ok] <tag> #1"
    assertThat(renderToMarkdown(listOf(Text(text)))).isEqualTo(text)
  }

  @Test
  fun `an empty part list renders to an empty string`() {
    assertThat(renderToMarkdown(emptyList())).isEmpty()
  }

  @Test
  fun `a dialect collapses to its root language name, lowercased`() {
    val out = renderToMarkdown(listOf(Code("SELECT 1", TestDialectLanguage)))
    assertThat(out).isEqualTo("```${TestBaseLanguage.id.lowercase()}\nSELECT 1\n```\n")
  }

  @Test
  fun `a dialect of a dialect still collapses to the root`() {
    val out = renderToMarkdown(listOf(Code("SELECT 1", TestSubDialectLanguage)))
    assertThat(out).isEqualTo("```${TestBaseLanguage.id.lowercase()}\nSELECT 1\n```\n")
  }

  @Test
  fun `a language with no base language uses its own id`() {
    assertThat(renderToMarkdown(listOf(Code("hello", PlainTextLanguage.INSTANCE))))
      .isEqualTo("```text\nhello\n```\n")
  }

  @Test
  fun `fence grows past the longest backtick run in the code`() {
    val out = renderToMarkdown(listOf(Code("a ``` b", PlainTextLanguage.INSTANCE)))
    assertThat(out).isEqualTo("````text\na ``` b\n````\n")
  }

  @Test
  fun `fence starts on its own line when the previous part did not end one`() {
    val out = renderToMarkdown(listOf(Text("query:"), Code("SELECT 1", PlainTextLanguage.INSTANCE)))
    assertThat(out).isEqualTo("query:\n```text\nSELECT 1\n```\n")
  }

  @Test
  fun `no extra newline is inserted when the previous part already ended a line`() {
    val out = renderToMarkdown(listOf(Text("query:\n"), Code("SELECT 1", PlainTextLanguage.INSTANCE)))
    assertThat(out).isEqualTo("query:  \n```text\nSELECT 1\n```\n")
  }

  @Test
  fun `code that already ends in a newline does not get a second one`() {
    assertThat(renderToMarkdown(listOf(Code("SELECT 1\n", PlainTextLanguage.INSTANCE))))
      .isEqualTo("```text\nSELECT 1\n```\n")
  }

  @Test
  fun `code is never escaped or hard-broken inside the fence`() {
    val code = "SELECT *\nFROM t WHERE name = 'O''Brien' -- _c_"

    assertThat(renderToMarkdown(listOf(Code(code, PlainTextLanguage.INSTANCE))))
      .isEqualTo("```text\n$code\n```\n")
  }

  @Test
  fun `bold and italic use markdown emphasis`() {
    assertThat(renderToMarkdown(listOf(Styled("x", setOf(FontStyle.BOLD))))).isEqualTo("**x**")
    assertThat(renderToMarkdown(listOf(Styled("x", setOf(FontStyle.ITALIC))))).isEqualTo("_x_")
  }

  @Test
  fun `underline is dropped, because clients strip the html that could carry it`() {
    assertThat(renderToMarkdown(listOf(Styled("x", setOf(FontStyle.UNDERLINE))))).isEqualTo("x")
  }

  @Test
  fun `every color is dropped`() {
    for (color in TextColor.entries) {
      assertThat(renderToMarkdown(listOf(Styled("x", color = color)))).isEqualTo("x")
    }
  }

  @Test
  fun `dropped underline and color leave the surviving emphasis intact`() {
    val styles = setOf(FontStyle.BOLD, FontStyle.ITALIC, FontStyle.UNDERLINE)

    assertThat(renderToMarkdown(listOf(Styled("x", styles, TextColor.GREEN)))).isEqualTo("**_x_**")
  }

  @Test
  fun `trailing newline is hoisted out of the emphasis markers`() {
    val out = renderToMarkdown(listOf(Styled("Title\n", setOf(FontStyle.BOLD))))
    assertThat(out).isEqualTo("**Title**$HARD_LINE_BREAK\n")
  }

  @Test
  fun `leading whitespace is hoisted out too`() {
    assertThat(renderToMarkdown(listOf(Styled(" x ", setOf(FontStyle.BOLD))))).isEqualTo(" **x** ")
  }

  @Test
  fun `whitespace-only styled text gets no wrappers`() {
    assertThat(renderToMarkdown(listOf(Styled("\n", setOf(FontStyle.BOLD))))).isEqualTo("$HARD_LINE_BREAK\n")
  }

  @Test
  fun `styled text with no styles and no color is unchanged`() {
    assertThat(renderToMarkdown(listOf(Styled("plain")))).isEqualTo("plain")
  }

  @Test
  fun `renderToMarkdown joins all three part kinds in order`() {
    val parts = listOf(
      Styled("Title\n", setOf(FontStyle.BOLD), TextColor.RED),
      Styled("datasource\n", setOf(FontStyle.UNDERLINE)),
      Code("SELECT 1", PlainTextLanguage.INSTANCE),
      Text("\nRun it?\n"),
    )

    val out = renderToMarkdown(parts)

    assertThat(out).isEqualTo(
      """**Title**$HARD_LINE_BREAK
        |datasource$HARD_LINE_BREAK
        |```text
        |SELECT 1
        |```
        |$HARD_LINE_BREAK
        |Run it?$HARD_LINE_BREAK
        |""".trimMargin()
    )
  }
}

private object TestBaseLanguage : Language("McpMarkdownRendererTestBase")
private object TestDialectLanguage : Language(TestBaseLanguage, "McpMarkdownRendererTestDialect")
private object TestSubDialectLanguage : Language(TestDialectLanguage, "McpMarkdownRendererTestSubDialect")

