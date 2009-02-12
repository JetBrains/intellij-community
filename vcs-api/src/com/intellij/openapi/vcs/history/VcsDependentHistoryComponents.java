package com.intellij.openapi.vcs.history;

import com.intellij.util.Consumer;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;

public class VcsDependentHistoryComponents {
  private final ColumnInfo[] myColumns;
  private final Consumer<VcsFileRevision> myRevisionListener;
  private final JComponent myDetailsComponent;

  public VcsDependentHistoryComponents(final ColumnInfo[] columns, final Consumer<VcsFileRevision> revisionListener, final JComponent detailsComponent) {
    myColumns = columns;
    myRevisionListener = revisionListener;
    myDetailsComponent = detailsComponent;
  }

  public static VcsDependentHistoryComponents createOnlyColumns(final ColumnInfo[] columns) {
    return new VcsDependentHistoryComponents(columns, null, null);
  }

  public ColumnInfo[] getColumns() {
    return myColumns;
  }

  public Consumer<VcsFileRevision> getRevisionListener() {
    return myRevisionListener;
  }

  public JComponent getDetailsComponent() {
    return myDetailsComponent;
  }
}
