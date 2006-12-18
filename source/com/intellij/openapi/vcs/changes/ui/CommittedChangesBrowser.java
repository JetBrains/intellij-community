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
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class CommittedChangesBrowser extends JPanel {
  private final TableView myChangeListsView;
  private final ChangesBrowser myChangesView;
  private ListTableModel<CommittedChangeList> myTableModel;
  private final JTextArea myCommitMessageArea;
  private CommittedChangeList mySelectedChangeList;
  private JPanel myLeftPanel;

  public CommittedChangesBrowser(final Project project, final ListTableModel<CommittedChangeList> tableModel) {
    super(new BorderLayout());

    myTableModel = tableModel;
    myTableModel.sortByColumn(0);    // switch to reverse sort
    myChangeListsView = new TableView(myTableModel);
    myChangeListsView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myChangesView = new ChangesBrowser(project, tableModel.getItems(), Collections.<Change>emptyList(), null, false, false);
    myChangesView.getListPanel().setBorder(null);

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

    myLeftPanel = new JPanel(new BorderLayout());
    myLeftPanel.add(new JScrollPane(myChangeListsView), BorderLayout.CENTER);
    myLeftPanel.add(commitPanel, BorderLayout.SOUTH);

    JSplitPane splitter = new JSplitPane();
    splitter.setLeftComponent(myLeftPanel);
    splitter.setRightComponent(myChangesView);

    add(splitter, BorderLayout.CENTER);

    updateBySelectionChange();
  }

  public void addToolBar(JComponent toolBar) {
    myLeftPanel.add(toolBar, BorderLayout.NORTH);
  }

  public void dispose() {
    myChangesView.dispose();
  }

  public void setModel(ListTableModel<CommittedChangeList> tableModel) {
    myTableModel = tableModel;
    myChangeListsView.setModel(tableModel);
  }

  private void updateBySelectionChange() {
    final int idx = myChangeListsView.getSelectionModel().getLeadSelectionIndex();
    final List<CommittedChangeList> items = myTableModel.getItems();
    CommittedChangeList list = (idx >= 0 && idx < items.size()) ? items.get(idx) : null;
    if (list != mySelectedChangeList) {
      mySelectedChangeList = list;
      myChangesView.setChangesToDisplay(list != null ? new ArrayList<Change>(list.getChanges()) : Collections.<Change>emptyList());
      myCommitMessageArea.setText(list != null ? list.getComment() : "");
    }
  }

  public CommittedChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }
}
