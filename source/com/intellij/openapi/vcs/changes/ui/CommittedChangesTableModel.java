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

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.ColumnInfo;

import java.util.List;
import java.util.Comparator;
import java.text.DateFormat;

public class CommittedChangesTableModel extends ListTableModel<CommittedChangeList> {
  private static ColumnInfo<? extends CommittedChangeList, String> COL_DATE = new ColumnInfo<CommittedChangeList, String>(
      VcsBundle.message("column.name.revision.list.date")) {
    public String valueOf(final CommittedChangeList item) {
      return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(item.getCommitDate());
    }

    public Comparator<CommittedChangeList> getComparator() {
      return new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          return o1.getCommitDate().compareTo(o2.getCommitDate());
        }
      };
    }
  };
  private static ColumnInfo<CommittedChangeList, String> COL_NAME = new ColumnInfo<CommittedChangeList, String>(
      VcsBundle.message("column.name.revision.list.committer")) {
    public String valueOf(final CommittedChangeList item) {
      return item.getCommitterName();
    }

    public Comparator<CommittedChangeList> getComparator() {
      return new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          return Comparing.compare(valueOf(o1), valueOf(o2));
        }
      };
    }
  };

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists) {
    super(new ColumnInfo[]{COL_DATE, COL_NAME}, changeLists, 0);
  }
}