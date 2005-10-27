/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseListPopupStep extends BaseStep implements ListPopupStep {

  private String myTitle;
  private List myValues;
  private List<Icon> myIcons;

  private int myDefaultOptionIndex = -1;

  public BaseListPopupStep(String aTitle, Object[] aValues) {
    this(aTitle, aValues, new Icon[]{});
  }
  public BaseListPopupStep(String aTitle, List aValues) {
    this(aTitle, aValues, new ArrayList<Icon>());
  }

  public BaseListPopupStep(String aTitle, Object[] aValues, Icon[] aIcons) {
    this(aTitle, Arrays.asList(aValues), Arrays.asList(aIcons));
  }

  public BaseListPopupStep(String aTitle, List aValues, Icon aSameIcon) {
    List<Icon> icons = new ArrayList<Icon>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < aValues.size(); i++) {
      icons.add(aSameIcon);
    }
    init(aTitle, aValues, icons);
  }

  public BaseListPopupStep(String aTitle, List aValues, List<Icon> aIcons) {
    init(aTitle, aValues, aIcons);
  }

  protected BaseListPopupStep() {
  }

  protected final void init(@Nullable String aTitle, List aValues, List<Icon> aIcons) {
    myTitle = aTitle;
    myValues = aValues;
    myIcons = aIcons;
  }

  @Nullable
  public final String getTitle() {
    return myTitle;
  }

  public final Object[] getValues() {
    //noinspection unchecked
    return myValues.toArray(new Object[myValues.size()]);
  }

  public PopupStep onChosen(Object selectedValue) {
    return FINAL_CHOICE;
  }

  public Icon getIconFor(Object aValue) {
    int index = myValues.indexOf(aValue);
    if (index != -1 && index < myIcons.size()) {
      return myIcons.get(index);
    }
    else {
      return null;
    }
  }

  public String getTextFor(Object value) {
    return value.toString();
  }

  public boolean isSelectable(Object value) {
    return true;
  }

  protected final int indexOf(Object aValue) {
    return myValues.indexOf(aValue);
  }

  public boolean hasSubstep(Object selectedValue) {
    return false;
  }

  public void canceled() {
  }

  public void setDefaultOptionIndex(int aDefaultOptionIndex) {
    myDefaultOptionIndex = aDefaultOptionIndex;
  }

  public int getDefaultOptionIndex() {
    return myDefaultOptionIndex;
  }

  public static class Speedsearch extends BaseListPopupStep {
    public Speedsearch(String aTitle, Object[] aValues) {
      super(aTitle, aValues);
    }

    public Speedsearch(String aTitle, Object[] aValues, Icon[] aIcons) {
      super(aTitle, aValues, aIcons);
    }

    public Speedsearch(String aTitle, List aValues, Icon aSameIcon) {
      super(aTitle, aValues, aSameIcon);
    }

    public Speedsearch(String aTitle, List aValues, List<Icon> aIcons) {
      super(aTitle, aValues, aIcons);
    }
  }

}
