package org.jetbrains.plugins.textmate.regex;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RegexFacadeTest {
  @Test
  public void matching() {
    RegexFacade regex = RegexFacade.regex("[0-9]+");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(0, 2), match.codePointRange(string.bytes));
  }

  @Test
  public void matchingFromPosition() {
    RegexFacade regex = RegexFacade.regex("[0-9]+");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, 2, -1, true, null);
    assertEquals(new TextMateRange(3, 5), match.codePointRange(string.bytes));
  }

  @Test
  public void matchingWithGroups() {
    RegexFacade regex = RegexFacade.regex("([0-9]+):([0-9]+)");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(0, 5), match.codePointRange(string.bytes));
    assertEquals(new TextMateRange(0, 2), match.codePointRange(string.bytes, 1));
    assertEquals(new TextMateRange(3, 5), match.codePointRange(string.bytes, 2));
  }

  @Test
  public void cyrillicMatchingSinceIndex() {
    RegexFacade regex = RegexFacade.regex("мир");
    String text = "привет, мир; привет, мир!";
    StringWithId string = new StringWithId(text);
    MatchData match = regex.match(string, RegexUtil.byteOffsetByCharOffset(text, 0, 9), -1, true, null);
    assertEquals(new TextMateRange(21, 24), match.codePointRange(string.bytes));
  }

  @Test
  public void cyrillicMatching() {
    RegexFacade regex = RegexFacade.regex("мир");
    StringWithId string = new StringWithId("привет, мир!");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(8, 11), match.codePointRange(string.bytes));
  }

  @Test
  public void unicodeMatching() {
    RegexFacade regex = RegexFacade.regex("мир");
    //noinspection NonAsciiCharacters
    String string = "\uD83D\uDEA7\uD83D\uDEA7\uD83D\uDEA7 привет, мир!";
    StringWithId stringWithId = new StringWithId(string);
    MatchData match = regex.match(stringWithId, null);
    TextMateRange range = match.charRange(string, stringWithId.bytes);
    assertEquals("мир", string.substring(range.start, range.end));
  }
}
