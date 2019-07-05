package org.jetbrains.plugins.textmate.regex;

import com.intellij.openapi.util.TextRange;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class SearcherTest {
  @Test
  public void simpleSearch() {
    RegexFacade regex = RegexFacade.regex("[0-9]+");
    byte[] stringBytes = "12:00pm".getBytes(StandardCharsets.UTF_8);
    Searcher searcher = regex.searcher(stringBytes);

    assertTrue(searcher.search());
    MatchData matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(0, 2), matchData.charOffset(stringBytes));

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(3, 5), matchData.charOffset(stringBytes));

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }

  @Test
  public void cyrillicSearch() {
    RegexFacade regex = RegexFacade.regex("мир");
    byte[] stringBytes = "привет, мир, привет, мир".getBytes(StandardCharsets.UTF_8);
    Searcher searcher = regex.searcher(stringBytes);

    assertTrue(searcher.search());
    MatchData matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(8, 11), matchData.charOffset(stringBytes));
    assertEquals(11, searcher.getCurrentCharPosition());

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(21, 24), matchData.charOffset(stringBytes));

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }

  @Test
  public void failedSearch() {
    RegexFacade regex = RegexFacade.regex("[0-9]+");
    byte[] stringBytes = "Failed search".getBytes(StandardCharsets.UTF_8);
    Searcher searcher = regex.searcher(stringBytes);
    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
    assertFalse(searcher.search());
  }

  @Test
  public void searchWithGroups() {
    RegexFacade regex = RegexFacade.regex("([0-9]+):([0-9]+)");
    byte[] stringBytes = "12:00pm 13:00pm".getBytes(StandardCharsets.UTF_8);
    Searcher searcher = regex.searcher(stringBytes);

    assertTrue(searcher.search());
    MatchData matchData = searcher.getCurrentMatchData();
    assertEquals(3, matchData.count());
    assertEquals(TextRange.create(0, 5), matchData.charOffset(stringBytes));
    assertEquals(TextRange.create(0, 2), matchData.charOffset(stringBytes, 1));
    assertEquals(TextRange.create(3, 5), matchData.charOffset(stringBytes, 2));

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(3, matchData.count());
    assertEquals(TextRange.create(8, 13), matchData.charOffset(stringBytes));
    assertEquals(TextRange.create(8, 10), matchData.charOffset(stringBytes, 1));
    assertEquals(TextRange.create(11, 13), matchData.charOffset(stringBytes, 2));

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }
}
