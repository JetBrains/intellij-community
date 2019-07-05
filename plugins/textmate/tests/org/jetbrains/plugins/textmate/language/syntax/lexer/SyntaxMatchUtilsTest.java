package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.regex.MatchData;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;
import static org.junit.Assert.assertEquals;

public class SyntaxMatchUtilsTest {
  @Test
  public void testReplaceGroupWithMatchData() {
    byte[] stringBytes = "first-second".getBytes(StandardCharsets.UTF_8);
    MatchData data = regex("([A-z]+)-([A-z]+)").match(stringBytes);
    assertEquals("first+second+first", SyntaxMatchUtils.replaceGroupsWithMatchData("\\1+\\2+\\1", stringBytes, data));
  }

  @Test
  public void testReplaceWithDollarSign() {
    byte[] stringBytes = "first-$".getBytes(StandardCharsets.UTF_8);
    MatchData data = regex("([A-z]+)-([A-z$]+)").match(stringBytes);
    assertEquals("first+\\$+first", SyntaxMatchUtils.replaceGroupsWithMatchData("\\1+\\2+\\1", stringBytes, data));
  }
}
