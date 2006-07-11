package com.intellij.openapi.vcs.ui.exclude;

import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SortedListModel;

import javax.swing.*;
import java.util.Comparator;

public class SortedComboBoxModel<T> extends SortedListModel<T> implements ComboBoxModel {
  private T mySelection;

  public SortedComboBoxModel(Comparator<T> comparator) {
    super(comparator);
  }

  public T getSelectedItem() {
    return mySelection;
  }

  public void setSelectedItem(Object anItem) {
    if (Comparing.equal(mySelection, anItem)) return;
    mySelection = (T)anItem;
    fireSelectionChanged();
  }

  private void fireSelectionChanged() {
    fireContentsChanged(this, -1, -1);
  }
}
