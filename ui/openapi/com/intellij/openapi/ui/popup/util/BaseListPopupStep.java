/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseListPopupStep<T> extends BaseStep<T> implements ListPopupStep<T> {

  private String myTitle;
  private List<T> myValues;
  private List<Icon> myIcons;

  private int myDefaultOptionIndex = -1;

  public BaseListPopupStep(String aTitle, T[] aValues) {
    this(aTitle, aValues, new Icon[]{});
  }
  public BaseListPopupStep(String aTitle, List<T> aValues) {
    this(aTitle, aValues, new ArrayList<Icon>());
  }

  public BaseListPopupStep(String aTitle, T[] aValues, Icon[] aIcons) {
    this(aTitle, Arrays.asList(aValues), Arrays.asList(aIcons));
  }

  public BaseListPopupStep(String aTitle, @NotNull List<T> aValues, Icon aSameIcon) {
    List<Icon> icons = new ArrayList<Icon>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < aValues.size(); i++) {
      icons.add(aSameIcon);
    }
    init(aTitle, aValues, icons);
  }

  public BaseListPopupStep(String aTitle, @NotNull List<T> aValues, List<Icon> aIcons) {
    init(aTitle, aValues, aIcons);
  }

  protected BaseListPopupStep() {
  }

  protected final void init(@Nullable String aTitle, @NotNull List<T> aValues, @Nullable List<Icon> aIcons) {
    myTitle = aTitle;
    myValues = aValues;
    myIcons = aIcons;
  }

  @Nullable
  public final String getTitle() {
    return myTitle;
  }

  @NotNull
  public final List<T> getValues() {
    return myValues;
  }

  public PopupStep onChosen(T selectedValue, final boolean finalChoice) {
    return FINAL_CHOICE;
  }

  public Icon getIconFor(T aValue) {
    int index = myValues.indexOf(aValue);
    if (index != -1 && myIcons != null && index < myIcons.size()) {
      return myIcons.get(index);
    }
    else {
      return null;
    }
  }

  @NotNull
  public String getTextFor(T value) {
    return value.toString();
  }

  public ListSeparator getSeparatorAbove(T value) {
    return null;
  }

  public boolean isSelectable(T value) {
    return true;
  }

  public boolean hasSubstep(T selectedValue) {
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
}
