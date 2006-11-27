/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.10.2006
 * Time: 18:54:43
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import java.util.Comparator;
import java.util.List;

public class CommittedChangesTableModel extends ListTableModel<CommittedChangeList> {
  private static final ChangeListColumn[] ourDefaultColumns = new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME };

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists) {
    super(buildColumnInfos(ourDefaultColumns), changeLists, 0);
  }

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists, final ChangeListColumn[] columns) {
    super(buildColumnInfos(columns), changeLists, 0);
  }

  private static ColumnInfo[] buildColumnInfos(final ChangeListColumn[] columns) {
    ColumnInfo[] result = new ColumnInfo[columns.length];
    for(int i=0; i<columns.length; i++) {
      result [i] = new ColumnInfoAdapter(columns [i]);
    }
    return result;
  }

  private static class ColumnInfoAdapter extends ColumnInfo {
    private ChangeListColumn myColumn;

    public ColumnInfoAdapter(ChangeListColumn column) {
      super(column.getTitle());
      myColumn = column;
    }

    public Object valueOf(final Object o) {
      //noinspection unchecked
      return myColumn.getValue((ChangeList)o);
    }

    @Override
    public Comparator getComparator() {
      return myColumn.getComparator();
    }
  }
}
