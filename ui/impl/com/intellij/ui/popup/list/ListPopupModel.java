/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.ui.popup.util.ElementFilter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ListPopupModel extends AbstractListModel {

  private List myOriginalList;
  private List myFilteredList = new ArrayList();

  private ElementFilter myFilter;
  private ListPopupStep myStep;

  private int myFullMatchIndex = -1;
  private int myStartsWithIndex = -1;

  public ListPopupModel(ElementFilter filter, ListPopupStep step) {
    myFilter = filter;
    myStep = step;
    myOriginalList = Collections.unmodifiableList(step.getValues());
    rebuildLists();
  }

  private void rebuildLists() {
    myFilteredList.clear();
    myFullMatchIndex = -1;
    myStartsWithIndex = -1;

    for (int i = 0; i < myOriginalList.size(); i++) {
      Object each = (Object) myOriginalList.get(i);
      if (myFilter.shouldBeShowing(each)) {
        addToFiltered(each);
      }
    }
  }

  private void addToFiltered(Object each) {
    myFilteredList.add(each);
    String filterString = myFilter.getSpeedSearch().getFilter().toUpperCase();
    String candidateString = each.toString().toUpperCase();
    int index = myFilteredList.size() - 1;

    if (filterString.equals(candidateString)) {
      myFullMatchIndex = index;
    }

    if (candidateString.startsWith(filterString)) {
      myStartsWithIndex = index;
    }
  }

  public int getSize() {
    return myFilteredList.size();
  }

  public Object getElementAt(int index) {
    return myFilteredList.get(index);
  }

  public boolean isSeparatorAboveOf(Object aValue) {
    return getSeparatorAbove(aValue) != null;
  }

  public String getCaptionAboveOf(Object value) {
    ListSeparator separator = getSeparatorAbove(value);
    if (separator != null) {
      return separator.getText();
    }
    return "";
  }

  private ListSeparator getSeparatorAbove(Object value) {
    return myStep.getSeparatorAbove(value);
  }

  public void refilter() {
    rebuildLists();
    if (myFilteredList.size() == 0 && myOriginalList.size() > 0) {
      myFilter.getSpeedSearch().backspace();
      refilter();
    }
    else {
      fireContentsChanged(this, 0, myFilteredList.size());
    }
  }

  public boolean isVisible(Object object) {
    return myFilteredList.indexOf(object) != -1;
  }

  public int getClosestMatchIndex() {
    return myFullMatchIndex != -1 ? myFullMatchIndex : myStartsWithIndex;
  }

}
