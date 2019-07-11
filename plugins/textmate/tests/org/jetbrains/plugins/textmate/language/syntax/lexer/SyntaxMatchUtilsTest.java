package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.StringWithId;
import org.junit.Test;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;
import static org.junit.Assert.assertEquals;

public class SyntaxMatchUtilsTest {
  @Test
  public void testReplaceGroupWithMatchData() {
    StringWithId string = new StringWithId("first-second");
    MatchData data = regex("([A-z]+)-([A-z]+)").match(string);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchData("\\1+\\2+\\1", string, data));
  }

  @Test
  public void testReplaceWithDollarSign() {
    StringWithId string = new StringWithId("first-$");
    MatchData data = regex("([A-z]+)-([A-z$]+)").match(string);
    assertEquals("first+\\$+first", SyntaxMatchUtils.replaceGroupsWithMatchData("\\1+\\2+\\1", string, data));
  }
}
