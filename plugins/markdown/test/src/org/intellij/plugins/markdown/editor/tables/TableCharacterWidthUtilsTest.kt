package org.intellij.plugins.markdown.editor.tables

import org.junit.Test
import org.junit.Assert.*

class TableCharacterWidthUtilsTest {

  @Test
  fun `test ASCII characters width calculation`() {
    assertEquals(1, TableCharacterWidthUtils.calculateDisplayWidth("a"))
    assertEquals(1, TableCharacterWidthUtils.calculateDisplayWidth("A"))
    assertEquals(1, TableCharacterWidthUtils.calculateDisplayWidth("1"))
    assertEquals(1, TableCharacterWidthUtils.calculateDisplayWidth("!"))
    assertEquals(5, TableCharacterWidthUtils.calculateDisplayWidth("hello"))
  }

  @Test
  fun `test Chinese characters width calculation`() {
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\u4E2D"))
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\u6587"))
    assertEquals(4, TableCharacterWidthUtils.calculateDisplayWidth("\u4E2D\u6587"))
    assertEquals(8, TableCharacterWidthUtils.calculateDisplayWidth("\u6295\u653E\u8D26\u53F7"))
  }

  @Test
  fun `test mixed content width calculation`() {
    assertEquals(3, TableCharacterWidthUtils.calculateDisplayWidth("a\u4E2D"))
    assertEquals(3, TableCharacterWidthUtils.calculateDisplayWidth("\u4E2Da"))
    assertEquals(7, TableCharacterWidthUtils.calculateDisplayWidth("hello\u4E2D"))
    assertEquals(7, TableCharacterWidthUtils.calculateDisplayWidth("\u4E2Dhello"))
  }

  @Test
  fun `test empty string width calculation`() {
    assertEquals(0, TableCharacterWidthUtils.calculateDisplayWidth(""))
  }

  @Test
  fun `test control characters width calculation`() {
    assertEquals(0, TableCharacterWidthUtils.calculateDisplayWidth("\t"))
    assertEquals(0, TableCharacterWidthUtils.calculateDisplayWidth("\n"))
    assertEquals(0, TableCharacterWidthUtils.calculateDisplayWidth("\r"))
  }

  @Test
  fun `test emoji width calculation`() {
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uD83D\uDE00"))
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uD83D\uDE80"))
    assertEquals(4, TableCharacterWidthUtils.calculateDisplayWidth("\uD83D\uDE00\uD83D\uDE80"))
  }

  @Test
  fun `test Japanese characters width calculation`() {
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\u3042")) // Hiragana a
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\u30A2")) // Katakana a
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\u6F22")) // Kanji
  }

  @Test
  fun `test Korean characters width calculation`() {
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uD55C")) // Hangul
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uAE00"))
  }

  @Test
  fun `test full-width ASCII variants width calculation`() {
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uFF21")) // Fullwidth A
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uFF11")) // Fullwidth 1
    assertEquals(2, TableCharacterWidthUtils.calculateDisplayWidth("\uFF01")) // Fullwidth !
  }

  @Test
  fun `test table cell content examples`() {
    // Test cases from the user's problem
    assertEquals(4, TableCharacterWidthUtils.calculateDisplayWidth("\u540D\u5B57"))     // 名字
    assertEquals(4, TableCharacterWidthUtils.calculateDisplayWidth("\u5E74\u9F84"))     // 年龄
    assertEquals(8, TableCharacterWidthUtils.calculateDisplayWidth("\u6295\u653E\u8D26\u53F7")) // 投放账号
    assertEquals(4, TableCharacterWidthUtils.calculateDisplayWidth("name"))
    assertEquals(3, TableCharacterWidthUtils.calculateDisplayWidth("age"))
    assertEquals(4, TableCharacterWidthUtils.calculateDisplayWidth("demo"))
  }
}
