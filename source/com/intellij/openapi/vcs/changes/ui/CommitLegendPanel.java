/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author max
 */
public class CommitLegendPanel {
  private JLabel myModifiedShown;
  private JLabel myModifiedIncluded;
  private JLabel myNewShown;
  private JLabel myNewIncluded;
  private JLabel myDeletedIncluded;
  private JLabel myTotalShown;
  private JLabel myTotalIncluded;
  private JPanel myRootPanel;
  private JLabel myDeletedShown;


  public CommitLegendPanel() {
    myRootPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("commit.legend.summary")));
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  public void update(final List<Change> displayedChanges, final List<Change> includedChanges) {
    updateCategory(myTotalShown, myTotalIncluded, displayedChanges, includedChanges, ALL_FILTER);
    updateCategory(myModifiedShown, myModifiedIncluded, displayedChanges, includedChanges, MODIFIED_FILTER);
    updateCategory(myNewShown, myNewIncluded, displayedChanges, includedChanges, NEW_FILTER);
    updateCategory(myDeletedShown, myDeletedIncluded, displayedChanges, includedChanges, DELETED_FILTER);
  }

  private interface Filter<T> {
    boolean matches(T item);
  }

  private static Filter<Change> MODIFIED_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return item.getType() == Change.Type.MODIFICATION || item.getType() == Change.Type.MOVED;
    }
  };
  private static Filter<Change> NEW_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return item.getType() == Change.Type.NEW;
    }
  };
  private static Filter<Change> DELETED_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return item.getType() == Change.Type.DELETED;
    }
  };
  private static Filter<Change> ALL_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return true;
    }
  };

  private static <T> int countMatchingItems(List<T> items, Filter<T> filter) {
    int count = 0;
    for (T item : items) {
      if (filter.matches(item)) count++;
    }

    return count;
  }

  private static void updateCategory(JLabel totalLabel,
                                     JLabel includedLabel,
                                     List<Change> totalList,
                                     List<Change> includedList,
                                     Filter<Change> filter) {
    int totalCount = countMatchingItems(totalList, filter);
    int includedCount = countMatchingItems(includedList, filter);
    updateLabel(totalLabel, totalCount, false);
    updateLabel(includedLabel, includedCount, totalCount != includedCount);
  }

  private static void updateLabel(JLabel label, int count, boolean bold) {
    label.setText(String.valueOf(count));
    label.setEnabled(bold || count != 0);
    label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
  }
}
