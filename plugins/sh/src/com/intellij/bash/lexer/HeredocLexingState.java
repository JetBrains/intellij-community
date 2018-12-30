package com.intellij.bash.lexer;

import com.google.common.collect.Lists;

import java.util.LinkedList;

/**
 * Heredoc lexing state used in the lexer
 */
public class HeredocLexingState {
  private final LinkedList<HeredocMarkerInfo> expectedHeredocs = Lists.newLinkedList();

  public boolean isEmpty() {
    return expectedHeredocs.isEmpty();
  }

  public boolean isNextMarker(CharSequence markerText) {
    return !expectedHeredocs.isEmpty() && expectedHeredocs.peekFirst().nameEquals(markerText);
  }

  public boolean isExpectingEvaluatingHeredoc() {
    if (isEmpty()) {
      throw new IllegalStateException("isExpectingEvaluatingHeredoc called on an empty marker stack");
    }

    return !expectedHeredocs.isEmpty() && expectedHeredocs.peekFirst().evaluating;
  }

  public boolean isIgnoringTabs() {
    if (isEmpty()) {
      throw new IllegalStateException("isIgnoringTabs called on an empty marker stack");
    }

    return !expectedHeredocs.isEmpty() && expectedHeredocs.peekFirst().ignoreLeadingTabs;
  }

  public void pushMarker(CharSequence marker, boolean ignoreTabs) {
    expectedHeredocs.add(new HeredocMarkerInfo(marker, ignoreTabs));
  }

  public void popMarker(CharSequence marker) {
    if (!isNextMarker(HeredocSharedImpl.cleanMarker(marker.toString(), false))) {
      throw new IllegalStateException("Heredoc marker isn't expected to be removed: " + marker);
    }

    expectedHeredocs.removeFirst();
  }

  private static class HeredocMarkerInfo {
    final boolean ignoreLeadingTabs;
    final boolean evaluating;
    final CharSequence markerName;

    HeredocMarkerInfo(CharSequence markerText, boolean ignoreLeadingTabs) {
      String markerTextString = markerText.toString();

      this.markerName = HeredocSharedImpl.cleanMarker(markerTextString, ignoreLeadingTabs);
      this.evaluating = HeredocSharedImpl.isEvaluatingMarker(markerTextString);
      this.ignoreLeadingTabs = ignoreLeadingTabs;
    }

    boolean nameEquals(CharSequence markerText) {
      return this.markerName.equals(HeredocSharedImpl.cleanMarker(markerText.toString(), ignoreLeadingTabs));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HeredocMarkerInfo that = (HeredocMarkerInfo) o;

      if (ignoreLeadingTabs != that.ignoreLeadingTabs) return false;
      if (evaluating != that.evaluating) return false;
      return markerName != null ? markerName.equals(that.markerName) : that.markerName == null;

    }

    @Override
    public int hashCode() {
      int result = (ignoreLeadingTabs ? 1 : 0);
      result = 31 * result + (evaluating ? 1 : 0);
      result = 31 * result + (markerName != null ? markerName.hashCode() : 0);
      return result;
    }
  }
}
