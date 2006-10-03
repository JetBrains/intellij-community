/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author max
 */
public class CommittedChangesBrowser extends JPanel {
  private final TableView myChangeListsView;
  private final ChangesBrowser myChangesView;
  private final ListTableModel<CommittedChangeList> myTableModel;
  private final JTextArea myCommitMessageArea;

  public CommittedChangesBrowser(final Project project, final ListTableModel<CommittedChangeList> tableModel) {
    super(new BorderLayout());

    myTableModel = tableModel;
    myTableModel.sortByColumn(0);    // switch to reverse sort
    myChangeListsView = new TableView(myTableModel);
    myChangeListsView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JPanel changesPanel = new JPanel(new BorderLayout());
    changesPanel.add(new JScrollPane(myChangeListsView), BorderLayout.CENTER);
    changesPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("committed.changes.title")));

    JSplitPane splitter = new JSplitPane();
    splitter.setLeftComponent(changesPanel);

    myChangesView = new ChangesBrowser(project, tableModel.getItems(), Collections.<Change>emptyList(), null, false, false);
    splitter.setRightComponent(myChangesView);

    myChangeListsView.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    myCommitMessageArea = new JTextArea();
    myCommitMessageArea.setRows(3);
    myCommitMessageArea.setWrapStyleWord(true);
    myCommitMessageArea.setLineWrap(true);
    myCommitMessageArea.setEditable(false);

    JPanel commitPanel = new JPanel(new BorderLayout());
    commitPanel.add(new JScrollPane(myCommitMessageArea), BorderLayout.CENTER);
    commitPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("label.commit.comment")));
    

    add(splitter, BorderLayout.CENTER);
    add(commitPanel, BorderLayout.SOUTH);

    updateBySelectionChange();
  }

  public void dispose() {
    myChangesView.dispose();
  }

  private void updateBySelectionChange() {
    final int idx = myChangeListsView.getSelectionModel().getLeadSelectionIndex();
    CommittedChangeList list = idx >= 0 ? myTableModel.getItems().get(idx) : null;
    myChangesView.setChangesToDisplay(list != null ? new ArrayList<Change>(list.getChanges()) : Collections.<Change>emptyList());
    myCommitMessageArea.setText(list != null ? list.getComment() : "");
  }
}
