package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.StringWithId;
import org.junit.Test;

import static org.jetbrains.plugins.textmate.regex.joni.JoniRegexFacade.regex;
import static org.junit.Assert.assertEquals;

public class SyntaxMatchUtilsTest {
  @Test
  public void testReplaceGroupWithMatchData() {
    StringWithId string = new StringWithId("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data));
  }

  @Test
  public void testReplaceGroupWithMatchDataInCaptures() {
    StringWithId string = new StringWithId("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("$1+$2+$1", string, data));
  }

  @Test
  public void testReplaceWithUppercase() {
    StringWithId string = new StringWithId("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("FIRST+second+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("${1:/upcase}+$2+$1", string, data));
  }

  @Test
  public void testReplaceWithDowncase() {
    StringWithId string = new StringWithId("FIRST-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string, null);
    assertEquals("first+second+FIRST", SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures("${1:/downcase}+$2+$1", string, data));
  }

  @Test
  public void testReplaceWithDollarSign() {
    StringWithId string = new StringWithId("first-$");
    MatchData data = regex("([A-z]+)-([A-z$]+)").match(string, null);
    assertEquals("first+\\$+first", SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex("\\1+\\2+\\1", string, data));
  }
}
