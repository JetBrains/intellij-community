package com.intellij.openapi.util;

import junit.framework.TestCase;

/**
 * @author dyoma
 */
public class TextRangeTest extends TestCase {
  public void testSubstring() {
    assertEquals("abc", new TextRange(0, 3).substring("abc"));
    assertEquals("abc", new TextRange(0, 3).substring("abcd"));
    assertEquals("bc", new TextRange(1, 3).substring("abcd"));
    assertEquals("", new TextRange(2, 2).substring("abcd"));
  }

  public void testCutOut() {
    assertEquals(new TextRange(1, 5), new TextRange(1, 5).cutOut(new TextRange(0, 4)));
    assertEquals(new TextRange(2, 5), new TextRange(1, 5).cutOut(new TextRange(1, 4)));
    assertEquals(new TextRange(1, 4), new TextRange(1, 5).cutOut(new TextRange(0, 3)));
    assertEquals(new TextRange(3, 3), new TextRange(1, 5).cutOut(new TextRange(2, 2)));
    assertEquals(new TextRange(2, 5), new TextRange(1, 5).cutOut(new TextRange(1, 10)));
  }

  public void testShiftRight() {
    TextRange range = new TextRange(1, 2);
    assertEquals(new TextRange(2, 3), range.shiftRight(1));
    assertEquals(new TextRange(0, 1), range.shiftRight(-1));
    assertSame(range, range.shiftRight(0));
  }

  public void testReplace() {
    TextRange range = new TextRange(1, 3);
    assertEquals("0a345", range.replace("012345", "a"));
    assertEquals("0345", range.replace("012345", ""));
    assertEquals("0abcdef345", range.replace("012345", "abcdef"));
  }
}
