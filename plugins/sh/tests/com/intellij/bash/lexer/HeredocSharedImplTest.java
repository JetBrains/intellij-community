package com.intellij.bash.lexer;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class HeredocSharedImplTest {
  @Test
  public void testStartOffset() {
    assertEquals(0, HeredocSharedImpl.startMarkerTextOffset("$", false));
    assertEquals(0, HeredocSharedImpl.startMarkerTextOffset("$ABC", false));

    assertEquals(1, HeredocSharedImpl.startMarkerTextOffset("\"ABC\"", false));
    assertEquals(2, HeredocSharedImpl.startMarkerTextOffset("$\"ABC\"", false));
  }

  @Test
  public void testStartOffsetTabs() {
    assertEquals(0, HeredocSharedImpl.startMarkerTextOffset("$", true));
    assertEquals(2, HeredocSharedImpl.startMarkerTextOffset("\t\t$", true));
    assertEquals(3, HeredocSharedImpl.startMarkerTextOffset("\t\t\t$ABC", true));

    assertEquals(0, HeredocSharedImpl.startMarkerTextOffset("\t\"ABC\"", false));
    assertEquals(0, HeredocSharedImpl.startMarkerTextOffset("\t$\"ABC\"", false));
  }

  @Test
  public void testWrapMarker() {
    assertEquals("EOF_NEW", HeredocSharedImpl.wrapMarker("EOF_NEW", "EOF"));
    assertEquals("\"EOF_NEW\"", HeredocSharedImpl.wrapMarker("EOF_NEW", "\"EOF\""));
    assertEquals("\'EOF_NEW\'", HeredocSharedImpl.wrapMarker("EOF_NEW", "\'EOF\'"));
    assertEquals("\\EOF_NEW", HeredocSharedImpl.wrapMarker("EOF_NEW", "\\EOF"));

    assertEquals("$\"EOF_NEW\"", HeredocSharedImpl.wrapMarker("EOF_NEW", "$\"EOF\""));
    assertEquals("$\'EOF_NEW\'", HeredocSharedImpl.wrapMarker("EOF_NEW", "$\'EOF\'"));
  }

  @Test
  public void testIssue331() {
    assertEquals(0, HeredocSharedImpl.startMarkerTextOffset("\t\t", false));
    assertEquals(1, HeredocSharedImpl.startMarkerTextOffset("\t\t", true));
  }
}