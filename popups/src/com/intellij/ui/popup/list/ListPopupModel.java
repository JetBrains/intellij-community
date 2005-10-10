/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.ui.popup.util.ElementFilter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ListPopupModel extends AbstractListModel {

  private List myOriginalList;

  private List myFilteredList = new ArrayList();
  private List myFilteredListWithNoSeparators = new ArrayList();
  private boolean myContainsSeparators;

  private ElementFilter myFilter;

  private int myFullMatchIndex = -1;
  private int myStartsWithIndex = -1;

  public ListPopupModel(ElementFilter filter, Object[] aValues) {
    myFilter = filter;
    myOriginalList = Collections.unmodifiableList(Arrays.asList(aValues));
    rebuildLists();
  }

  private void rebuildLists() {
    myFilteredList.clear();
    myFilteredListWithNoSeparators.clear();
    myContainsSeparators = false;
    myFullMatchIndex = -1;
    myStartsWithIndex = -1;

    for (int i = 0; i < myOriginalList.size(); i++) {
      Object each = (Object) myOriginalList.get(i);
      if (each instanceof ListSeparator) {
        addToFiltered(each);
        myContainsSeparators = true;
      }
      else if (myFilter.shouldBeShowing(each)) {
        addToFiltered(each);
      }
    }

    for (int i = 0; i < myFilteredList.size(); i++) {
      Object each = myFilteredList.get(i);
      if (!(each instanceof ListSeparator)) {
        myFilteredListWithNoSeparators.add(each);
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
    return myFilteredListWithNoSeparators.size();
  }

  public Object getElementAt(int index) {
    return myFilteredListWithNoSeparators.get(index);
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
    final int index = myFilteredList.indexOf(value);
    if (index == -1 || index == 0) {
      return null;
    }
    Object separatorCandidate = myFilteredList.get(index - 1);
    if (separatorCandidate instanceof ListSeparator) {
      return (ListSeparator) separatorCandidate;
    }

    return null;
  }

  public void refilter() {
    rebuildLists();
    if (myFilteredListWithNoSeparators.size() == 0 && myOriginalList.size() > 0) {
      myFilter.getSpeedSearch().backspace();
      refilter();
    }
    else {
      fireContentsChanged(this, 0, myFilteredListWithNoSeparators.size());
    }
  }

  public boolean isVisible(Object object) {
    return myFilteredList.indexOf(object) != -1;
  }

  public int getClosestMatchIndex() {
    return myFullMatchIndex != -1 ? myFullMatchIndex : myStartsWithIndex;
  }

}
