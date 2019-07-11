package org.jetbrains.plugins.textmate.regex;

import com.intellij.openapi.util.TextRange;
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
    MatchData match = regex.match(string);
    assertEquals(TextRange.create(0, 2), match.charOffset(string.bytes));
  }

  @Test
  public void matchingFromPosition() {
    RegexFacade regex = RegexFacade.regex("[0-9]+");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string, 2);
    assertEquals(TextRange.create(3, 5), match.charOffset(string.bytes));
  }

  @Test
  public void matchingWithGroups() {
    RegexFacade regex = RegexFacade.regex("([0-9]+):([0-9]+)");
    StringWithId string = new StringWithId("12:00pm");
    MatchData match = regex.match(string);
    assertEquals(TextRange.create(0, 5), match.charOffset(string.bytes));
    assertEquals(TextRange.create(0, 2), match.charOffset(string.bytes, 1));
    assertEquals(TextRange.create(3, 5), match.charOffset(string.bytes, 2));
  }

  @Test
  public void creatingSearcher() {
    RegexFacade regex = RegexFacade.regex("[0-9]+");
    byte[] stringBytes = "12:00pm".getBytes(StandardCharsets.UTF_8);
    Searcher searcher = regex.searcher(stringBytes);
    List<MatchData> regions = new ArrayList<>();
    while (searcher.search()) {
      regions.add(searcher.getCurrentMatchData());
    }
    assertEquals(2, regions.size());
    assertEquals(TextRange.create(0, 2), regions.get(0).charOffset(stringBytes));
    assertEquals(TextRange.create(3, 5), regions.get(1).charOffset(stringBytes));
  }

  @Test
  public void cyrillicMatchingSinceIndex() {
    RegexFacade regex = RegexFacade.regex("мир");
    String text = "привет, мир; привет, мир!";
    StringWithId string = new StringWithId(text);
    MatchData match = regex.match(string, RegexUtil.byteOffsetByCharOffset(text, 9));
    assertEquals(TextRange.create(21, 24), match.charOffset(string.bytes));
  }

  @Test
  public void cyrillicMatching() {
    RegexFacade regex = RegexFacade.regex("мир");
    StringWithId string = new StringWithId("привет, мир!");
    MatchData match = regex.match(string);
    assertEquals(TextRange.create(8, 11), match.charOffset(string.bytes));
  }
}
