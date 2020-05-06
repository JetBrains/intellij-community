package org.jetbrains.plugins.textmate.regex;

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
    assertEquals(new TextMateRange(0, 2), matchData.codePointRange(stringBytes));

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(new TextMateRange(3, 5), matchData.codePointRange(stringBytes));

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
    assertEquals(new TextMateRange(8, 11), matchData.codePointRange(stringBytes));
    assertEquals(11, searcher.getCurrentCharPosition());

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(new TextMateRange(21, 24), matchData.codePointRange(stringBytes));

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
    assertEquals(new TextMateRange(0, 5), matchData.codePointRange(stringBytes));
    assertEquals(new TextMateRange(0, 2), matchData.codePointRange(stringBytes, 1));
    assertEquals(new TextMateRange(3, 5), matchData.codePointRange(stringBytes, 2));

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(3, matchData.count());
    assertEquals(new TextMateRange(8, 13), matchData.codePointRange(stringBytes));
    assertEquals(new TextMateRange(8, 10), matchData.codePointRange(stringBytes, 1));
    assertEquals(new TextMateRange(11, 13), matchData.codePointRange(stringBytes, 2));

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }
}
