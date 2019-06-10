package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.regex.MatchData;
import org.junit.Test;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;
import static org.junit.Assert.assertEquals;

public class SyntaxMatchUtilsTest {
  @Test
  public void testReplaceGroupWithMatchData() {
    final String dataString = "first-second";
    final MatchData data = regex("([A-z]+)-([A-z]+)").match(dataString);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchData("\\1+\\2+\\1", data));
  }
  
  @Test
  public void testReplaceWithDollarSign() {
    final String dataString = "first-$";
    final MatchData data = regex("([A-z]+)-([A-z$]+)").match(dataString);
    assertEquals("first+\\$+first", SyntaxMatchUtils.replaceGroupsWithMatchData("\\1+\\2+\\1", data));
  }
}
