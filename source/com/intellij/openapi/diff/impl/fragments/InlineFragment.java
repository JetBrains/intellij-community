package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.DiffMarkup;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;

import java.security.InvalidParameterException;

public class InlineFragment implements Fragment {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.fragments.InlineFragment");
  private final TextRange myRange1;
  private final TextRange myRange2;
  private final TextDiffType myType;

  public InlineFragment(TextDiffType type, TextRange range1, TextRange range2) {
    myType = type;
    myRange1 = range1;
    myRange2 = range2;
  }

  public TextDiffType getType() {
    return myType;
  }

  public TextRange getRange(FragmentSide side) {
    if (side == FragmentSide.SIDE1) return myRange1;
    if (side == FragmentSide.SIDE2) return myRange2;
    throw new InvalidParameterException(String.valueOf(side));
  }

  public Fragment shift(TextRange range1, TextRange range2, int startingLine1, int startingLine2) {
    return new InlineFragment(myType,
                              LineFragment.shiftRange(range1, myRange1),
                              LineFragment.shiftRange(range2, myRange2));
  }

  public void highlight(DiffMarkup appender1, DiffMarkup appender2, boolean isLast) {
    appender1.highlightText(this, true);
    appender2.highlightText(this, true);
  }

  public Fragment getSubfragmentAt(int offset, FragmentSide side, Condition<Fragment> condition) {
    LOG.assertTrue(getRange(side).getStartOffset() <= offset &&
                   offset < getRange(side).getEndOffset() &&
                   condition.value(this));
    return this;
  }
}
