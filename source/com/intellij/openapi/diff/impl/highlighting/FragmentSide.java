package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ex.DiffFragment;

import java.security.InvalidParameterException;

public abstract class FragmentSide {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.highlighting.FragmentSide");
  public abstract String getText(DiffFragment fragment);
  public abstract DiffFragment createFragment(String text, String otherText, boolean modified);
  public abstract FragmentSide otherSide();
  public abstract int getIndex();
  public abstract int getMergeIndex();

  public String getOtherText(DiffFragment fragment) {
    return otherSide().getText(fragment);
  }

  public InvalidParameterException invalidException() {
    return new InvalidParameterException(String.valueOf(this));
  }

  public static final FragmentSide SIDE1 = new FragmentSide() {
    public String getText(DiffFragment fragment) {
      return fragment.getText1();
    }

    public DiffFragment createFragment(String text, String otherText, boolean modified) {
      DiffFragment fragment = new DiffFragment(text, otherText);
      if (!fragment.isOneSide()) fragment.setModified(modified);
      return fragment;
    }

    public FragmentSide otherSide() {
      return SIDE2;
    }

    public int getIndex() {
      return 0;
    }

    public int getMergeIndex() {
      return 0;
    }
  };

  public static final FragmentSide SIDE2 = new FragmentSide() {
    public String getText(DiffFragment fragment) {
      return fragment.getText2();
    }

    public DiffFragment createFragment(String text, String otherText, boolean modified) {
      DiffFragment fragment = new DiffFragment(otherText, text);
      if (!fragment.isOneSide()) fragment.setModified(modified);
      return fragment;
    }

    public FragmentSide otherSide() {
      return SIDE1;
    }

    public int getIndex() {
      return 1;
    }

    public int getMergeIndex() {
      return 2;
    }
  };

  public static FragmentSide chooseSide(DiffFragment oneSide) {
    LOG.assertTrue(oneSide.isOneSide());
    LOG.assertTrue(oneSide.getText1() != oneSide.getText2());
    return oneSide.getText1() == null ? SIDE2 : SIDE1;
  }

  public static FragmentSide fromIndex(int index) {
    switch (index) {
      case 0: return SIDE1;
      case 1: return SIDE2;
      default: throw new InvalidParameterException(String.valueOf(index));
    }
  }
}
