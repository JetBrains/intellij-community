package com.intellij.mcpserver.elicitation

import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Code
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.BOLD
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.ITALIC
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle.UNDERLINE
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Styled
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Text
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.TextColor.YELLOW
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [renderToText]: joins the [ElicitationMessagePart.text] of each part in order with no separator.
 * Language, font styles, and color are ignored — the renderer emits plain text only.
 */
class TextElicitationFormRendererTest {

  @Test
  fun `text part is emitted verbatim`() {
    assertThat(listOf(Text("hello\nworld")).joinToString<ElicitationMessagePart>("") { it.text }).isEqualTo("hello\nworld")
  }

  @Test
  fun `code part text is emitted verbatim, language is ignored`() {
    val code = "SELECT * FROM t WHERE name = 'O''Brien' -- comment"
    assertThat(listOf(Code(code, PlainTextLanguage.INSTANCE)).joinToString<ElicitationMessagePart>("") { it.text }).isEqualTo(code)
  }

  @Test
  fun `styled part text is emitted verbatim, styles and color are ignored`() {
    val styled = Styled("!", setOf(BOLD, ITALIC, UNDERLINE), YELLOW)
    assertThat(listOf(styled).joinToString<ElicitationMessagePart>("") { it.text }).isEqualTo("!")
  }

  @Test
  fun `parts joined in order with no separator`() {
    val parts = listOf(
      Text("-- "),
      Code("abc", PlainTextLanguage.INSTANCE),
      Styled("!", setOf(BOLD), YELLOW),
    )
    assertThat(parts.joinToString("") { it.text }).isEqualTo("-- abc!")
  }

  @Test
  fun `empty list renders to empty string`() {
    assertThat(emptyList<ElicitationMessagePart>().joinToString("") { it.text }).isEqualTo("")
  }

}
