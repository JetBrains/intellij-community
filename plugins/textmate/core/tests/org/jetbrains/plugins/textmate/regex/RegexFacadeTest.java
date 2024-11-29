package org.jetbrains.plugins.textmate.regex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RegexFacadeTest {
  @Test
  public void matching() {
    RegexFacade regex = regex("[0-9]+");
    TextMateString string = new TextMateString("12:00pm");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(0, 2), match.codePointRange(string.bytes));
  }

  @Test
  public void matchingFromPosition() {
    RegexFacade regex = regex("[0-9]+");
    TextMateString string = new TextMateString("12:00pm");
    MatchData match = regex.match(string, 2, -1, true, null);
    assertEquals(new TextMateRange(3, 5), match.codePointRange(string.bytes));
  }

  @Test
  public void matchingWithGroups() {
    RegexFacade regex = regex("([0-9]+):([0-9]+)");
    TextMateString string = new TextMateString("12:00pm");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(0, 5), match.codePointRange(string.bytes));
    assertEquals(new TextMateRange(0, 2), match.codePointRange(string.bytes, 1));
    assertEquals(new TextMateRange(3, 5), match.codePointRange(string.bytes, 2));
  }

  @Test
  public void cyrillicMatchingSinceIndex() {
    RegexFacade regex = regex("мир");
    String text = "привет, мир; привет, мир!";
    TextMateString string = new TextMateString(text);
    MatchData match = regex.match(string, RegexUtil.byteOffsetByCharOffset(text, 0, 9), -1, true, null);
    assertEquals(new TextMateRange(21, 24), match.codePointRange(string.bytes));
  }

  @Test
  public void cyrillicMatching() {
    RegexFacade regex = regex("мир");
    TextMateString string = new TextMateString("привет, мир!");
    MatchData match = regex.match(string, null);
    assertEquals(new TextMateRange(8, 11), match.codePointRange(string.bytes));
  }

  @Test
  public void unicodeMatching() {
    RegexFacade regex = regex("мир");
    //noinspection NonAsciiCharacters
    String string = "\uD83D\uDEA7\uD83D\uDEA7\uD83D\uDEA7 привет, мир!";
    TextMateString textMateString = new TextMateString(string);
    MatchData match = regex.match(textMateString, null);
    TextMateRange range = match.charRange(string, textMateString.bytes);
    assertEquals("мир", string.substring(range.start, range.end));
  }

  private static @NotNull RegexFacade regex(String s) {
    return new JoniRegexFactory().regex(s);
  }
}
