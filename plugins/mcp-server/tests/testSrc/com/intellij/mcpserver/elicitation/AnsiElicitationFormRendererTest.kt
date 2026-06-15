package com.intellij.mcpserver.elicitation

import com.intellij.lang.Language
import com.intellij.lexer.DummyLexer
import com.intellij.lexer.Lexer
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Code
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.FontStyle
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Styled
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.Text
import com.intellij.mcpserver.elicitation.ElicitationMessagePart.TextColor
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.Color


/**
 * Tests for [renderToAnsi]: the `Language` -> ANSI converter used by the CLI elicitation form.
 * Checks that the text is kept as is (removing ANSI gives back the input) and that colors and
 * font styles are written as SGR codes.
 */
@TestApplication
class AnsiElicitationFormRendererTest {

  @Test
  fun `unknown language falls back to verbatim text`() {
    val code = "SELECT * FROM t WHERE name = 'O''Brien' -- comment"
    // Plain text has no real highlighter, so the text comes back unchanged.
    assertThat(renderToAnsi(listOf(Code(code, PlainTextLanguage.INSTANCE)))).isEqualTo(code)
  }

  @Test
  fun `highlighted output preserves text and emits SGR color`(
    @TestDisposable disposable: Disposable
  ) {
    disposable.initTestHighlighter(TestHighlighterFactory)

    val code = "abc"
    val out = renderToAnsi(listOf(Code(code, TestLanguage)))

    // ANSI escapes must not change the text.
    assertThat(stripAnsi(out)).isEqualTo(code)
    // If the scheme gives the token a foreground, it is written as a 24-bit color SGR.
    val fg = EditorColorsUtil.getGlobalOrDefaultColorScheme().getAttributes(KEYWORD)?.foregroundColor
    if (fg != null) {
      assertThat(out).contains("$ESC[$SGR_FOREGROUND;$SGR_TRUE_COLOR;${fg.red};${fg.green};${fg.blue}m")
      assertThat(out).endsWith(RESET)
    }
  }

  @Test
  fun `combined bold and italic token emits both font SGR codes`(
    @TestDisposable disposable: Disposable,
  ) {
    disposable.initTestHighlighter(TestHighlighterFactory)
    disposable.overrideAttributes(KEYWORD, boldItalicNoColors())

    val out = renderToAnsi(listOf(Code("abc", TestLanguage)))

    assertThat(stripAnsi(out)).isEqualTo("abc")
    assertThat(out).contains("$ESC[${SGR_BOLD}m")
    assertThat(out).contains("$ESC[${SGR_ITALIC}m")
  }

  @Test
  fun `renderToAnsi joins parts in order, highlights code, styles emphasis, keeps text verbatim`(
    @TestDisposable disposable: Disposable,
  ) {
    disposable.initTestHighlighter(TestHighlighterFactory)

    val parts = listOf(
      Text("-- "),
      Code("abc", TestLanguage),
      Styled("!", setOf(FontStyle.BOLD), TextColor.YELLOW),
    )
    val out = renderToAnsi(parts)

    // Parts are joined with no separator; removing ANSI gives back the joined text.
    assertThat(stripAnsi(out)).isEqualTo("-- abc!")
    // The leading plain text has no escape before it.
    assertThat(out).startsWith("-- ")
    // Styled text adds its color code and bold.
    assertThat(out).contains("$ESC[${TextColor.YELLOW.sgrCode}m")
    assertThat(out).contains("$ESC[${SGR_BOLD}m")
  }

  @Test
  fun `styled color maps to its basic ANSI code`() {
    fun colorCodeOf(color: TextColor): String =
      renderToAnsi(listOf(Styled("x", color = color)))

    for (color in TextColor.entries) {
      assertThat(colorCodeOf(color)).isEqualTo("$ESC[${color.sgrCode}mx$RESET")
    }
  }

  @Test
  fun `token with several keys merges them, most specific wins`(
    @TestDisposable disposable: Disposable,
  ) {
    disposable.initTestHighlighter(MultiKeyHighlighterFactory)
    val black = Color(10, 20, 30)
    val grey = Color(40, 50, 60)
    disposable.overrideAttributes(LOW_KEY, coloredPlain(black))
    disposable.overrideAttributes(HIGH_KEY, coloredPlain(grey))

    val out = renderToAnsi(listOf(Code("x", TestLanguage)))

    // getTokenHighlights returns keys least -> most specific; the last one must win.
    assertThat(out).contains("$ESC[$SGR_FOREGROUND;$SGR_TRUE_COLOR;${grey.red};${grey.green};${grey.blue}m")
    assertThat(out).doesNotContain("$ESC[$SGR_FOREGROUND;$SGR_TRUE_COLOR;${black.red};${black.green};${black.blue}m")
  }

}

private fun coloredPlain(fgColor: Color): TextAttributes = TextAttributes(
  fgColor,
  null,
  null,
  null,
  Font.PLAIN
)

// fontType is a bitmask: a bold+italic token must emit both codes; an `==` check would miss them.
private fun boldItalicNoColors(): TextAttributes = TextAttributes(
  null,
  null,
  null,
  null,
  Font.BOLD or Font.ITALIC
)

private fun Disposable.initTestHighlighter(highlighterFactory: SyntaxHighlighterFactory) =
  SyntaxHighlighterFactory.getLanguageFactory()
    .addExplicitExtension(TestLanguage, highlighterFactory, this)

/**
 * Sets [attributes] for [key] on the global scheme and restores the previous value when [this]
 * is disposed (the test's [TestDisposable]), so tests don't need their own try/finally.
 */
private fun Disposable.overrideAttributes(key: TextAttributesKey, attributes: TextAttributes) {
  val scheme = EditorColorsUtil.getGlobalOrDefaultColorScheme()
  val previous = scheme.getAttributes(key)
  scheme.setAttributes(key, attributes)
  Disposer.register(this) { scheme.setAttributes(key, previous) }
}

private fun stripAnsi(s: String): String = s.replace(Regex("$ESC\\[[0-9;]*m"), "")

private object TestLanguage : Language("HighlightToAnsiTestLanguage")

private val TEST_TOKEN = IElementType("HIGHLIGHT_TO_ANSI_TEST_TOKEN", TestLanguage)

private object TestHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = TestHighlighter
}

/** Treats the whole input as one keyword-colored token. */
private object TestHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = DummyLexer(TEST_TOKEN)
  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(KEYWORD)
}

private val LOW_KEY = TextAttributesKey.createTextAttributesKey("ANSI_TEST_LOW_KEY")
private val HIGH_KEY = TextAttributesKey.createTextAttributesKey("ANSI_TEST_HIGH_KEY")

private object MultiKeyHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = MultiKeyHighlighter
}

/** Returns the whole input as one token carrying two keys, the least specific first. */
private object MultiKeyHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = DummyLexer(TEST_TOKEN)
  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(LOW_KEY, HIGH_KEY)
}
