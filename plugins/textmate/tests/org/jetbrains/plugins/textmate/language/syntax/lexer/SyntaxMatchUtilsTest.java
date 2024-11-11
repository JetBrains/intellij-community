package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.RegexFacade;
import org.jetbrains.plugins.textmate.regex.TextMateString;
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyntaxMatchUtilsTest {
  @Test
  public void testReplaceGroupWithMatchData() {
    TextMateString string = new TextMateString("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data));
  }

  @Test
  public void testReplaceGroupWithMatchDataInCaptures() {
    TextMateString string = new TextMateString("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("$1+$2+$1", string, data));
  }

  @Test
  public void testReplaceWithUppercase() {
    TextMateString string = new TextMateString("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("FIRST+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("${1:/upcase}+$2+$1", string, data));
  }

  @Test
  public void testReplaceWithDowncase() {
    TextMateString string = new TextMateString("FIRST-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("first+second+FIRST", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("${1:/downcase}+$2+$1", string, data));
  }

  @Test
  public void testReplaceWithDollarSign() {
    TextMateString string = new TextMateString("first-$");
    MatchData data = regex("([A-z]+)-([A-z$]+)").match(string, null);
    assertEquals("first+\\$+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data));
  }

  private static RegexFacade regex(String pattern) {
    return new JoniRegexFactory().regex(pattern);
  }
}
