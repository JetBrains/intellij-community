package org.jetbrains.plugins.textmate.regex;

import com.intellij.openapi.util.TextRange;
import org.junit.Test;

import static org.junit.Assert.*;

public class SearcherTest {
  @Test
  public void simpleSearch() {
    final RegexFacade regex = RegexFacade.regex("[0-9]+");
    final Searcher searcher = regex.searcher("12:00pm");

    assertTrue(searcher.search());
    MatchData matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(0, 2), matchData.offset());

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(3, 5), matchData.offset());

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }

  @Test
  public void cyrillicSearch() {
    final RegexFacade regex = RegexFacade.regex("мир");
    final Searcher searcher = regex.searcher("привет, мир, привет, мир");

    assertTrue(searcher.search());
    MatchData matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(8, 11), matchData.offset());
    assertEquals(11, searcher.getCurrentCharPosition());

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(1, matchData.count());
    assertEquals(TextRange.create(21, 24), matchData.offset());

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }

  @Test
  public void failedSearch() {
    final RegexFacade regex = RegexFacade.regex("[0-9]+");
    final String searchString = "Failed search";
    final Searcher searcher = regex.searcher(searchString);
    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
    assertFalse(searcher.search());
  }

  @Test
  public void searchWithGroups() {
    final RegexFacade regex = RegexFacade.regex("([0-9]+):([0-9]+)");
    final Searcher searcher = regex.searcher("12:00pm 13:00pm");

    assertTrue(searcher.search());
    MatchData matchData = searcher.getCurrentMatchData();
    assertEquals(3, matchData.count());
    assertEquals(TextRange.create(0, 5), matchData.offset());
    assertEquals(TextRange.create(0, 2), matchData.offset(1));
    assertEquals(TextRange.create(3, 5), matchData.offset(2));

    assertTrue(searcher.search());
    matchData = searcher.getCurrentMatchData();
    assertEquals(3, matchData.count());
    assertEquals(TextRange.create(8, 13), matchData.offset());
    assertEquals(TextRange.create(8, 10), matchData.offset(1));
    assertEquals(TextRange.create(11, 13), matchData.offset(2));

    assertFalse(searcher.search());
    assertEquals(-1, searcher.getCurrentCharPosition());
  }
}
