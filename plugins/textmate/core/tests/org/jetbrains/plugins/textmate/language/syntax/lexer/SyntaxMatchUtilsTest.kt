package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateStringImpl.Companion.fromString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SyntaxMatchUtilsTest {
  @Test
  fun testReplaceGroupWithMatchData() {
    val string = fromString("first-second")
    val data = MatchData(matched = true, offsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("first+second+first", replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }

  @Test
  fun testReplaceGroupWithMatchDataInCaptures() {
    val string = fromString("first-second")
    val data = MatchData(matched = true, offsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("first+second+first", replaceGroupsWithMatchDataInCaptures("$1+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithUppercase() {
    val string = fromString("first-second")
    val data = MatchData(matched = true, offsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("FIRST+second+first", replaceGroupsWithMatchDataInCaptures("\${1:/upcase}+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithDowncase() {
    val string = fromString("FIRST-second")
    val data = MatchData(matched = true, offsets = intArrayOf(0, 12, 0, 5, 6, 12))
    assertEquals("first+second+FIRST", replaceGroupsWithMatchDataInCaptures("\${1:/downcase}+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithDollarSign() {
    val string = fromString("first-$")
    val data = MatchData(matched = true, offsets = intArrayOf(0, 7, 0, 5, 6, 7))
    assertEquals("first+\\$+first", replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }
}
