package com.intellij.bash.lexer;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class HeredocMarkerInfoTest {
  @Test
  public void testIsEmpty() {
    HeredocLexingState state = new HeredocLexingState();
    Assert.assertTrue(state.isEmpty());

    state.pushMarker("NAME", false);
    Assert.assertFalse(state.isEmpty());
    state.popMarker("NAME");

    Assert.assertTrue(state.isEmpty());
  }

  @Test
  public void testTabs() {
    HeredocLexingState state = new HeredocLexingState();

    state.pushMarker("NAME", false);
    Assert.assertFalse(state.isIgnoringTabs());
    state.popMarker("NAME");

    state.pushMarker("NAME", true);
    Assert.assertTrue(state.isIgnoringTabs());
    state.popMarker("NAME");

    Assert.assertTrue(state.isEmpty());
  }

  @Test
  public void testMarkerMatching() {
    HeredocLexingState state = new HeredocLexingState();

    state.pushMarker("NAME", false);
    Assert.assertTrue(state.isNextMarker("NAME"));
    Assert.assertFalse(state.isNextMarker("\tNAME"));
  }

  @Test
  public void testMarkerMatchingIngoredTabs() {
    HeredocLexingState state = new HeredocLexingState();

    state.pushMarker("NAME", true);
    Assert.assertTrue(state.isNextMarker("NAME"));
    Assert.assertTrue(state.isNextMarker("\tNAME"));
  }
}