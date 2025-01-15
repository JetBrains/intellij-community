package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.TextMateString.Companion.fromString
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory
import org.junit.Test
import kotlin.test.assertEquals

class SyntaxMatchUtilsTest {
  @Test
  fun testReplaceGroupWithMatchData() {
    val string = fromString("first-second")
    val data: MatchData = regex("([A-z]+)-([A-z]+)").match(string, null)
    assertEquals("first+second+first", replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }

  @Test
  fun testReplaceGroupWithMatchDataInCaptures() {
    val string = fromString("first-second")
    val data: MatchData = regex("([A-z]+)-([A-z]+)").match(string, null)
    assertEquals("first+second+first", replaceGroupsWithMatchDataInCaptures("$1+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithUppercase() {
    val string = fromString("first-second")
    val data: MatchData = regex("([A-z]+)-([A-z]+)").match(string, null)
    assertEquals("FIRST+second+first", replaceGroupsWithMatchDataInCaptures("\${1:/upcase}+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithDowncase() {
    val string = fromString("FIRST-second")
    val data: MatchData = regex("([A-z]+)-([A-z]+)").match(string, null)
    assertEquals("first+second+FIRST", replaceGroupsWithMatchDataInCaptures("\${1:/downcase}+$2+$1", string, data))
  }

  @Test
  fun testReplaceWithDollarSign() {
    val string = fromString("first-$")
    val data: MatchData = regex("([A-z]+)-([A-z$]+)").match(string, null)
    assertEquals("first+\\$+first", replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data))
  }

  companion object {
    private fun regex(pattern: String): RegexFacade {
      return JoniRegexFactory().regex(pattern)
    }
  }
}
