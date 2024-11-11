package org.jetbrains.plugins.textmate.regex;

import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFacade;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RegexFacadeTest {
  @Test
  public void matching() {
    JoniRegexFacade regex = JoniRegexFacade.regex("[0-9]+");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(0, 2), match.codePointRange(string.bytes));
  }

  @Test
  public void matchingFromPosition() {
    JoniRegexFacade regex = JoniRegexFacade.regex("[0-9]+");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, 2, -1, true, null);
    assertEquals(new TextMateRange(3, 5), match.codePointRange(string.bytes));
  }

  @Test
  public void matchingWithGroups() {
    JoniRegexFacade regex = JoniRegexFacade.regex("([0-9]+):([0-9]+)");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(0, 5), match.codePointRange(string.bytes));
    assertEquals(new TextMateRange(0, 2), match.codePointRange(string.bytes, 1));
    assertEquals(new TextMateRange(3, 5), match.codePointRange(string.bytes, 2));
  }

  @Test
  public void cyrillicMatchingSinceIndex() {
    JoniRegexFacade regex = JoniRegexFacade.regex("мир");
    String text = "привет, мир; привет, мир!";
    StringWithId string = new StringWithId(text);
    MatchData match = regex.match(string, RegexUtil.byteOffsetByCharOffset(text, 0, 9), -1, true, null);
    assertEquals(new TextMateRange(21, 24), match.codePointRange(string.bytes));
  }

  @Test
  public void cyrillicMatching() {
    JoniRegexFacade regex = JoniRegexFacade.regex("мир");
    StringWithId string = new StringWithId("привет, мир!");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(8, 11), match.codePointRange(string.bytes));
  }

  @Test
  public void unicodeMatching() {
    JoniRegexFacade regex = JoniRegexFacade.regex("мир");
    //noinspection NonAsciiCharacters
    String string = "\uD83D\uDEA7\uD83D\uDEA7\uD83D\uDEA7 привет, мир!";
    StringWithId stringWithId = new StringWithId(string);
    MatchData match = regex.match(stringWithId, null);
    TextMateRange range = match.charRange(string, stringWithId.bytes);
    assertEquals("мир", string.substring(range.start, range.end));
  }
}
