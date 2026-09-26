// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.jediterm.terminal.TextStyle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.junit.Test

class TerminalOutputPatternTest {
  // Parsing tests

  @Test
  fun `parse plain text`() {
    val pattern = outputPattern("hello world")
    assertThat(pattern.text).isEqualTo("hello world")
    assertThat(pattern.styles).isEmpty()
    assertThat(pattern.cursorOffset).isNull()
  }

  @Test
  fun `parse empty string`() {
    val pattern = outputPattern("")
    assertThat(pattern.text).isEqualTo("")
    assertThat(pattern.styles).isEmpty()
    assertThat(pattern.cursorOffset).isNull()
  }

  @Test
  fun `parse empty style tag produces no style range`() {
    val pattern = outputPattern("<s1></s1>")
    assertThat(pattern.text).isEqualTo("")
    assertThat(pattern.styles).isEmpty()
  }

  @Test
  fun `parse single style`() {
    val pattern = outputPattern("hello <s1>world</s1>")
    assertThat(pattern.text).isEqualTo("hello world")
    assertThat(pattern.styles).containsExactly(
      styleRange(6, 11, TerminalOutputPattern.STYLES[0])
    )
  }

  @Test
  fun `parse multiple styles`() {
    val pattern = outputPattern("hello <s1>world</s1><s2>!</s2>")
    assertThat(pattern.text).isEqualTo("hello world!")
    assertThat(pattern.styles).containsExactly(
      styleRange(6, 11, TerminalOutputPattern.STYLES[0]),
      styleRange(11, 12, TerminalOutputPattern.STYLES[1]),
    )
  }

  @Test
  fun `parse cursor inside text`() {
    val pattern = outputPattern("ab<cursor>cde")
    assertThat(pattern.text).isEqualTo("abcde")
    assertThat(pattern.cursorOffset).isEqualTo(2)
    assertThat(pattern.styles).isEmpty()
  }

  @Test
  fun `parse cursor at start`() {
    val pattern = outputPattern("<cursor>hello")
    assertThat(pattern.text).isEqualTo("hello")
    assertThat(pattern.cursorOffset).isEqualTo(0)
  }

  @Test
  fun `parse cursor at end`() {
    val pattern = outputPattern("hello<cursor>")
    assertThat(pattern.text).isEqualTo("hello")
    assertThat(pattern.cursorOffset).isEqualTo(5)
  }

  @Test
  fun `parse same style number used twice`() {
    val pattern = outputPattern("<s1>aaa</s1> <s1>bbb</s1>")
    assertThat(pattern.text).isEqualTo("aaa bbb")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 3, TerminalOutputPattern.STYLES[0]),
      styleRange(4, 7, TerminalOutputPattern.STYLES[0]),
    )
  }

  @Test
  fun `parse non-adjacent styles with unstyled gap`() {
    val pattern = outputPattern("<s1>aa</s1>xx<s2>bb</s2>")
    assertThat(pattern.text).isEqualTo("aaxxbb")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 2, TerminalOutputPattern.STYLES[0]),
      styleRange(4, 6, TerminalOutputPattern.STYLES[1]),
    )
  }

  @Test
  fun `parse style covering entire text`() {
    val pattern = outputPattern("<s1>hello</s1>")
    assertThat(pattern.text).isEqualTo("hello")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 5, TerminalOutputPattern.STYLES[0])
    )
  }

  @Test
  fun `parse cursor only with empty text`() {
    val pattern = outputPattern("<cursor>")
    assertThat(pattern.text).isEqualTo("")
    assertThat(pattern.cursorOffset).isEqualTo(0)
    assertThat(pattern.styles).isEmpty()
  }

  @Test
  fun `parse cursor on second line`() {
    val pattern = outputPattern("aaa\nb<cursor>bb")
    assertThat(pattern.text).isEqualTo("aaa\nbbb")
    assertThat(pattern.cursorOffset).isEqualTo(5)
  }

  @Test
  fun `parse single character style`() {
    val pattern = outputPattern("x<s3>y</s3>z")
    assertThat(pattern.text).isEqualTo("xyz")
    assertThat(pattern.styles).containsExactly(
      styleRange(1, 2, TerminalOutputPattern.STYLES[2])
    )
  }

  @Test
  fun `parse cursor with styles`() {
    val pattern = outputPattern("hello <s1>world</s1><s2>!</s2>\nab<cursor>cde")
    assertThat(pattern.text).isEqualTo("hello world!\nabcde")
    assertThat(pattern.styles).containsExactly(
      styleRange(6, 11, TerminalOutputPattern.STYLES[0]),
      styleRange(11, 12, TerminalOutputPattern.STYLES[1]),
    )
    assertThat(pattern.cursorOffset).isEqualTo(15)
  }

  @Test
  fun `parse multiline text`() {
    val pattern = outputPattern("line1\nline2\nline3")
    assertThat(pattern.text).isEqualTo("line1\nline2\nline3")
    assertThat(pattern.styles).isEmpty()
  }

  @Test
  fun `parse style on each line`() {
    val pattern = outputPattern("<s1>aaa</s1>\n<s2>bbb</s2>")
    assertThat(pattern.text).isEqualTo("aaa\nbbb")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 3, TerminalOutputPattern.STYLES[0]),
      styleRange(4, 7, TerminalOutputPattern.STYLES[1]),
    )
  }

  @Test
  fun `parse style spanning multiple lines`() {
    val pattern = outputPattern("<s1>hello\nworld</s1>")
    assertThat(pattern.text).isEqualTo("hello\nworld")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 11, TerminalOutputPattern.STYLES[0])
    )
  }

  @Test
  fun `parse all nine styles`() {
    val pattern = outputPattern("<s1>1</s1><s2>2</s2><s3>3</s3><s4>4</s4><s5>5</s5><s6>6</s6><s7>7</s7><s8>8</s8><s9>9</s9>")
    assertThat(pattern.text).isEqualTo("123456789")
    assertThat(pattern.styles).hasSize(9)
    for (i in 0 until 9) {
      assertThat(pattern.styles[i]).isEqualTo(
        styleRange(i.toLong(), (i + 1).toLong(), TerminalOutputPattern.STYLES[i])
      )
    }
  }

  @Test
  fun `parse cursor inside styled text`() {
    val pattern = outputPattern("<s1>he<cursor>llo</s1>")
    assertThat(pattern.text).isEqualTo("hello")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 5, TerminalOutputPattern.STYLES[0])
    )
    assertThat(pattern.cursorOffset).isEqualTo(2)
  }

  @Test
  fun `parse cursor at start of styled text`() {
    val pattern = outputPattern("<cursor><s1>hello</s1> world")
    assertThat(pattern.text).isEqualTo("hello world")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 5, TerminalOutputPattern.STYLES[0])
    )
    assertThat(pattern.cursorOffset).isEqualTo(0)
  }

  @Test
  fun `parse cursor at end of styled text`() {
    val pattern = outputPattern("<s1>hello</s1><cursor> world")
    assertThat(pattern.text).isEqualTo("hello world")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 5, TerminalOutputPattern.STYLES[0])
    )
    assertThat(pattern.cursorOffset).isEqualTo(5)
  }

  @Test
  fun `parse cursor between two styled regions`() {
    val pattern = outputPattern("<s1>aaa</s1><cursor><s2>bbb</s2>")
    assertThat(pattern.text).isEqualTo("aaabbb")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 3, TerminalOutputPattern.STYLES[0]),
      styleRange(3, 6, TerminalOutputPattern.STYLES[1]),
    )
    assertThat(pattern.cursorOffset).isEqualTo(3)
  }

  // Parsing tests: OSC8 links

  @Test
  fun `parse single link`() {
    val pattern = outputPattern("hello <a href=\"https://example.com\">world</a>")
    assertThat(pattern.text).isEqualTo("hello world")
    assertThat(pattern.osc8Hyperlinks).containsExactly(
      osc8Hyperlink(6, 11, "https://example.com")
    )
  }

  @Test
  fun `parse empty link tag produces no link`() {
    val pattern = outputPattern("<a href=\"https://example.com\"></a>")
    assertThat(pattern.text).isEqualTo("")
    assertThat(pattern.osc8Hyperlinks).isEmpty()
  }

  @Test
  fun `parse multiple links`() {
    val pattern = outputPattern("<a href=\"https://a\">aa</a> <a href=\"https://b\">bb</a>")
    assertThat(pattern.text).isEqualTo("aa bb")
    assertThat(pattern.osc8Hyperlinks).containsExactly(
      osc8Hyperlink(0, 2, "https://a"),
      osc8Hyperlink(3, 5, "https://b"),
    )
  }

  @Test
  fun `parse link with cursor inside`() {
    val pattern = outputPattern("<a href=\"https://example.com\">wor<cursor>ld</a>")
    assertThat(pattern.text).isEqualTo("world")
    assertThat(pattern.osc8Hyperlinks).containsExactly(
      osc8Hyperlink(0, 5, "https://example.com")
    )
    assertThat(pattern.cursorOffset).isEqualTo(3)
  }

  @Test
  fun `parse link and style side by side`() {
    val pattern = outputPattern("<s1>hi</s1> <a href=\"https://example.com\">there</a>")
    assertThat(pattern.text).isEqualTo("hi there")
    assertThat(pattern.styles).containsExactly(styleRange(0, 2, TerminalOutputPattern.STYLES[0]))
    assertThat(pattern.osc8Hyperlinks).containsExactly(osc8Hyperlink(3, 8, "https://example.com"))
  }

  // Parsing validation tests

  @Test
  fun `nested style tags throw`() {
    assertThatThrownBy { outputPattern("<s1>hello <s2>world</s2></s1>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Nested")
  }

  @Test
  fun `multiple cursors throw`() {
    assertThatThrownBy { outputPattern("<cursor>hello<cursor>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Multiple")
  }

  @Test
  fun `unknown tag throws`() {
    assertThatThrownBy { outputPattern("<bold>text</bold>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Unknown tag")
  }

  @Test
  fun `cursor at start boundary inside style tag throws`() {
    assertThatThrownBy { outputPattern("<s1><cursor>hello</s1>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("boundary")
  }

  @Test
  fun `cursor at end boundary inside style tag throws`() {
    assertThatThrownBy { outputPattern("<s1>hello<cursor></s1>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("boundary")
  }

  @Test
  fun `s0 tag throws`() {
    assertThatThrownBy { outputPattern("<s0>text</s0>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Unknown tag")
  }

  @Test
  fun `s10 tag throws`() {
    assertThatThrownBy { outputPattern("<s10>text</s10>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Unknown tag")
  }

  @Test
  fun `unclosed style tag - JSoup auto-closes`() {
    // JSoup XML parser auto-closes unclosed tags; the result is still a valid pattern
    val pattern = outputPattern("<s1>hello")
    assertThat(pattern.text).isEqualTo("hello")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 5, TerminalOutputPattern.STYLES[0])
    )
  }

  @Test
  fun `mismatched closing tag - JSoup auto-corrects`() {
    // JSoup XML parser silently ignores mismatched closing tag and auto-closes <s1>
    val pattern = outputPattern("<s1>hello</s2>")
    assertThat(pattern.text).isEqualTo("hello")
    assertThat(pattern.styles).containsExactly(
      styleRange(0, 5, TerminalOutputPattern.STYLES[0])
    )
  }

  @Test
  fun `link without href throws`() {
    assertThatThrownBy { outputPattern("<a>text</a>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("href")
  }

  @Test
  fun `nested link tags throw`() {
    assertThatThrownBy { outputPattern("<a href=\"https://a\">hello <a href=\"https://b\">world</a></a>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Nested")
  }

  @Test
  fun `style nested inside link throws`() {
    assertThatThrownBy { outputPattern("<a href=\"https://example.com\"><s1>text</s1></a>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Nested")
  }

  @Test
  fun `link nested inside style throws`() {
    assertThatThrownBy { outputPattern("<s1><a href=\"https://example.com\">text</a></s1>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Nested")
  }

  @Test
  fun `link spanning multiple lines throws`() {
    assertThatThrownBy { outputPattern("<a href=\"https://example.com\">hello\nworld</a>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("multiple lines")
  }

  @Test
  fun `cursor at boundary inside link throws`() {
    assertThatThrownBy { outputPattern("<a href=\"https://example.com\"><cursor>hello</a>") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("boundary")
  }

  // toString round-trip tests

  @Test
  fun `toString plain text`() {
    assertToStringRoundTrip("hello world")
  }

  @Test
  fun `toString with styles`() {
    assertToStringRoundTrip("hello <s1>world</s1><s2>!</s2>")
  }

  @Test
  fun `toString with cursor`() {
    assertToStringRoundTrip("ab<cursor>cde")
  }

  @Test
  fun `toString with cursor and styles`() {
    assertToStringRoundTrip("hello <s1>world</s1><s2>!</s2>\nab<cursor>cde")
  }

  @Test
  fun `toString empty`() {
    assertToStringRoundTrip("")
  }

  @Test
  fun `toString cursor at left boundary of style`() {
    assertToStringRoundTrip("<cursor><s1>hello</s1>")
  }

  @Test
  fun `toString cursor at right boundary of style`() {
    assertToStringRoundTrip("<s1>hello</s1><cursor>")
  }

  @Test
  fun `toString cursor inside styled text`() {
    assertToStringRoundTrip("<s1>he<cursor>llo</s1>")
  }

  @Test
  fun `toString cursor between styles`() {
    assertToStringRoundTrip("<s1>aaa</s1><cursor><s2>bbb</s2>")
  }

  @Test
  fun `toString multiline with styles`() {
    assertToStringRoundTrip("<s1>aaa</s1>\n<s2>bbb</s2>")
  }

  @Test
  fun `toString same style used twice`() {
    assertToStringRoundTrip("<s1>aaa</s1> <s1>bbb</s1>")
  }

  @Test
  fun `toString non-adjacent styles`() {
    assertToStringRoundTrip("<s1>aa</s1>xx<s2>bb</s2>")
  }

  @Test
  fun `toString cursor only`() {
    assertToStringRoundTrip("<cursor>")
  }

  @Test
  fun `toString with link`() {
    assertToStringRoundTrip("hello <a href=\"https://example.com\">world</a>")
  }

  @Test
  fun `toString with link and cursor`() {
    assertToStringRoundTrip("<a href=\"https://example.com\">wor<cursor>ld</a>")
  }

  @Test
  fun `toString with link and style side by side`() {
    assertToStringRoundTrip("<s1>hi</s1> <a href=\"https://example.com\">there</a>")
  }

  private fun assertToStringRoundTrip(input: String) {
    val pattern = outputPattern(input)
    val reconstructed = pattern.toString()
    assertThat(reconstructed).isEqualTo(input)
    assertThat(outputPattern(reconstructed)).isEqualTo(pattern)
  }

  // Equals / hashCode tests

  @Test
  fun `plain text patterns are equal`() {
    assertThat(outputPattern("hello")).isEqualTo(outputPattern("hello"))
  }

  @Test
  fun `empty patterns are equal`() {
    assertThat(outputPattern("")).isEqualTo(outputPattern(""))
  }

  @Test
  fun `same pattern is equal`() {
    val a = outputPattern("<s1>hello</s1> <s2>world</s2>")
    val b = outputPattern("<s1>hello</s1> <s2>world</s2>")
    assertThat(a).isEqualTo(b)
    assertThat(a.hashCode()).isEqualTo(b.hashCode())
  }

  @Test
  fun `different text not equal`() {
    assertThat(outputPattern("abc")).isNotEqualTo(outputPattern("def"))
  }

  @Test
  fun `different cursor offset not equal`() {
    assertThat(outputPattern("a<cursor>b")).isNotEqualTo(outputPattern("ab<cursor>"))
  }

  @Test
  fun `cursor vs no cursor not equal`() {
    assertThat(outputPattern("<cursor>ab")).isNotEqualTo(outputPattern("ab"))
  }

  @Test
  fun `adjacent same-style ranges equal merged range`() {
    val split = outputPattern("<s1>a</s1><s1>b</s1>")
    val merged = outputPattern("<s1>ab</s1>")
    assertThat(split).isEqualTo(merged)
    assertThat(split.hashCode()).isEqualTo(merged.hashCode())
  }

  @Test
  fun `multiple adjacent same-style ranges equal merged range`() {
    val split = outputPattern("<s1>a</s1><s1>b</s1><s1>c</s1>")
    val merged = outputPattern("<s1>abc</s1>")
    assertThat(split).isEqualTo(merged)
    assertThat(split.hashCode()).isEqualTo(merged.hashCode())
  }

  @Test
  fun `adjacent same-style ranges preserve toString`() {
    val pattern = outputPattern("<s1>a</s1><s1>b</s1>")
    assertThat(pattern.toString()).isEqualTo("<s1>a</s1><s1>b</s1>")
  }

  @Test
  fun `non-adjacent same-style ranges are not equal to single range`() {
    val split = outputPattern("<s1>a</s1>x<s1>b</s1>")
    val single = outputPattern("<s1>axb</s1>")
    assertThat(split).isNotEqualTo(single)
  }

  @Test
  fun `adjacent different-style ranges are not equal to single range`() {
    val twoStyles = outputPattern("<s1>a</s1><s2>b</s2>")
    val oneStyle = outputPattern("<s1>ab</s1>")
    assertThat(twoStyles).isNotEqualTo(oneStyle)
  }

  @Test
  fun `adjacent same-style with cursor between them equals merged`() {
    val split = outputPattern("<s1>a</s1><cursor><s1>b</s1>")
    val merged = outputPattern("<s1>a<cursor>b</s1>")
    assertThat(split).isEqualTo(merged)
    assertThat(split.hashCode()).isEqualTo(merged.hashCode())
  }

  @Test
  fun `partially adjacent same-style - only adjacent pair merged`() {
    val split = outputPattern("<s1>a</s1><s1>b</s1> <s1>c</s1>")
    val partialMerge = outputPattern("<s1>ab</s1> <s1>c</s1>")
    assertThat(split).isEqualTo(partialMerge)
    assertThat(split.hashCode()).isEqualTo(partialMerge.hashCode())
  }

  @Test
  fun `same link pattern is equal`() {
    val a = outputPattern("<a href=\"https://example.com\">hello</a>")
    val b = outputPattern("<a href=\"https://example.com\">hello</a>")
    assertThat(a).isEqualTo(b)
    assertThat(a.hashCode()).isEqualTo(b.hashCode())
  }

  @Test
  fun `different link uri not equal`() {
    assertThat(outputPattern("<a href=\"https://a\">hello</a>"))
      .isNotEqualTo(outputPattern("<a href=\"https://b\">hello</a>"))
  }

  @Test
  fun `link vs no link not equal`() {
    assertThat(outputPattern("<a href=\"https://example.com\">hello</a>")).isNotEqualTo(outputPattern("hello"))
  }

  private fun osc8Hyperlink(startOffset: Long, endOffset: Long, uri: String): Osc8Hyperlink {
    return Osc8Hyperlink(startOffset, endOffset, uri)
  }

  private fun styleRange(
    startOffset: Long,
    endOffset: Long,
    style: TextStyle,
  ): StyleRange {
    return StyleRange(startOffset, endOffset, style, ignoreContrastAdjustment = false)
  }
}