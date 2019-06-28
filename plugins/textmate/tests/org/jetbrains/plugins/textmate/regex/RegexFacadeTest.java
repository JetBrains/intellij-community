package org.jetbrains.plugins.textmate.regex;

import com.intellij.openapi.util.TextRange;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RegexFacadeTest {
  @Test
  public void matching() {
    final RegexFacade regex = RegexFacade.regex("[0-9]+");
    final MatchData match = regex.match("12:00pm");
    assertEquals(TextRange.create(0, 2), match.offset());
  }

  @Test
  public void matchingFromPosition() {
    final RegexFacade regex = RegexFacade.regex("[0-9]+");
    final MatchData match = regex.match("12:00pm", 2);
    assertEquals(TextRange.create(3, 5), match.offset());
  }

  @Test
  public void matchingWithGroups() {
    final RegexFacade regex = RegexFacade.regex("([0-9]+):([0-9]+)");
    final MatchData match = regex.match("12:00pm");
    assertEquals(TextRange.create(0, 5), match.offset());
    assertEquals(TextRange.create(0, 2), match.offset(1));
    assertEquals(TextRange.create(3, 5), match.offset(2));
  }

  @Test
  public void creatingSearcher() {
    final RegexFacade regex = RegexFacade.regex("[0-9]+");
    final Searcher searcher = regex.searcher("12:00pm");
    List<MatchData> regions = new ArrayList<>();
    while (searcher.search()) {
      regions.add(searcher.getCurrentMatchData());
    }
    assertEquals(2, regions.size());
    assertEquals(TextRange.create(0, 2), regions.get(0).offset());
    assertEquals(TextRange.create(3, 5), regions.get(1).offset());
  }

  @Test
  public void cyrillicMatchingSinceIndex() {
    final RegexFacade regex = RegexFacade.regex("мир");
    final MatchData match = regex.match("привет, мир; привет, мир!", 9);
    assertEquals(TextRange.create(21, 24), match.offset());
  }

  @Test
  public void cyrillicMatching() {
    final RegexFacade regex = RegexFacade.regex("мир");
    final MatchData match = regex.match("привет, мир!");
    assertEquals(TextRange.create(8, 11), match.offset());
  }
}
