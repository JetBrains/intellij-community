package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateStringImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SyntaxMatchUtilsTest {
  @Test
  fun testReplaceGroupWithMatchData() {
    val string = TextMateStringImpl.Companion.fromString("first-second")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }

  @Test
  fun testReplaceGroupWithUnmatchedMatchData() {
    val string = TextMateStringImpl.Companion.fromString("first-second")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 12, 0, 5, -1, -1))
    assertEquals("first++first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }

  @Test
  fun testReplaceWithDollarSign() {
    val string = TextMateStringImpl.Companion.fromString("first-$")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 7, 0, 5, 6, 7))
    assertEquals("first+\\$+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }

  @Test
  fun testReplaceGroupWithMatchDataInCaptures() {
    val string = TextMateStringImpl.Companion.fromString("first-second")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("$1+$2+$1", string, data))
  }

  @Test
  fun testReplaceGroupWithUnmatchedMatchDataInCaptures() {
    val string = TextMateStringImpl.Companion.fromString("first-second")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 12, 0, 5, -1, -1))
    assertEquals("first++first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("$1+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithUppercase() {
    val string = TextMateStringImpl.Companion.fromString("first-second")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("FIRST+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("\${1:/upcase}+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithDowncase() {
    val string = TextMateStringImpl.Companion.fromString("FIRST-second")
    val data = MatchData(matched = true, byteOffsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("first+second+FIRST", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("\${1:/downcase}+$2+$1", string, data))
  }
}