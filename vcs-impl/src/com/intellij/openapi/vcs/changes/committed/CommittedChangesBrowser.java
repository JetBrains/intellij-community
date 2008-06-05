/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class CommittedChangesBrowser extends JPanel {
  private Project myProject;
  private final TableView<CommittedChangeList> myChangeListsView;
  private final ChangesBrowser myChangesView;
  private CommittedChangesTableModel myTableModel;
  private final JEditorPane myCommitMessageArea;
  private CommittedChangeList mySelectedChangeList;
  private JPanel myLeftPanel;

  public CommittedChangesBrowser(final Project project, final CommittedChangesTableModel tableModel) {
    super(new BorderLayout());

    myProject = project;
    myTableModel = tableModel;
    myTableModel.sortByChangesColumn(ChangeListColumn.DATE, SortableColumnModel.SORT_DESCENDING);
    myChangeListsView = new TableView<CommittedChangeList>(myTableModel);
    myChangeListsView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myChangesView = new RepositoryChangesBrowser(project, tableModel.getItems());
    myChangesView.getListPanel().setBorder(null);

    myChangeListsView.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    myCommitMessageArea = new JEditorPane(UIUtil.HTML_MIME, "");
    myCommitMessageArea.setBackground(UIUtil.getComboBoxDisabledBackground());
    myCommitMessageArea.addHyperlinkListener(new BrowserHyperlinkListener());
    myCommitMessageArea.setPreferredSize(new Dimension(150, 100));
    myCommitMessageArea.setEditable(false);

    JPanel commitPanel = new JPanel(new BorderLayout());
    commitPanel.add(new JScrollPane(myCommitMessageArea), BorderLayout.CENTER);
    final JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myCommitMessageArea);
    commitPanel.add(separator, BorderLayout.NORTH);

    myLeftPanel = new JPanel(new GridBagLayout());
    myLeftPanel.add(new JScrollPane(myChangeListsView), new GridBagConstraints(0, 0, 2, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(2,2,2,2), 0, 0));
    if (tableModel instanceof CommittedChangesNavigation) {
      final CommittedChangesNavigation navigation = (CommittedChangesNavigation) tableModel;

      final JButton backButton = new JButton("<");
      final JButton forwardButton = new JButton(">");

      backButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          try {
            navigation.goBack();
            backButton.setEnabled(navigation.canGoBack());
          }
          catch (VcsException e1) {
            Messages.showErrorDialog(e1.getMessage(), "");
            backButton.setEnabled(false);
          }
          forwardButton.setEnabled(navigation.canGoForward());
          selectFirstIfAny();
        }
      });
      forwardButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          navigation.goForward();
          backButton.setEnabled(navigation.canGoBack());
          forwardButton.setEnabled(navigation.canGoForward());
          selectFirstIfAny();
        }
      });
      backButton.setEnabled(navigation.canGoBack());
      forwardButton.setEnabled(navigation.canGoForward());

      myLeftPanel.add(backButton, new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2,2,2,2), 0, 0));
      myLeftPanel.add(forwardButton, new GridBagConstraints(1, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2,2,2,2), 0, 0));
    }

    JSplitPane leftSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    leftSplitter.setTopComponent(myLeftPanel);
    leftSplitter.setBottomComponent(commitPanel);
    leftSplitter.setDividerLocation(0.6);
    leftSplitter.setResizeWeight(0.5);

    JSplitPane splitter = new JSplitPane();
    splitter.setLeftComponent(leftSplitter);
    splitter.setRightComponent(myChangesView);

    add(splitter, BorderLayout.CENTER);

    selectFirstIfAny();
  }

  private void selectFirstIfAny() {
    if (myTableModel.getRowCount() > 0) {
      TableUtil.selectRows(myChangeListsView, new int[]{0});
    }
  }

  public void addToolBar(JComponent toolBar) {
    myLeftPanel.add(toolBar, BorderLayout.NORTH);
  }

  public void dispose() {
    myChangesView.dispose();
  }

  public void setModel(CommittedChangesTableModel tableModel) {
    ChangeListColumn sortColumn = myTableModel.getSortColumn();
    int sortingType = myTableModel.getSortingType();
    myTableModel = tableModel;
    myTableModel.sortByChangesColumn(sortColumn, sortingType);
    myChangeListsView.setModel(tableModel);
    tableModel.fireTableStructureChanged();
  }

  public void setItems(List<CommittedChangeList> items) {
    myTableModel.setItems(items);
  }

  private void updateBySelectionChange() {
    final int idx = myChangeListsView.getSelectionModel().getLeadSelectionIndex();
    final List<CommittedChangeList> items = myTableModel.getItems();
    CommittedChangeList list = (idx >= 0 && idx < items.size()) ? items.get(idx) : null;
    if (list != mySelectedChangeList) {
      mySelectedChangeList = list;
      myChangesView.setChangesToDisplay(list != null ? new ArrayList<Change>(list.getChanges()) : Collections.<Change>emptyList());
      myCommitMessageArea.setText(list != null ? formatText(list) : "");
      myCommitMessageArea.select(0, 0);
    }
  }

  private String formatText(final CommittedChangeList list) {
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + 
           "</head><body>" + IssueLinkHtmlRenderer.formatTextWithLinks(myProject, list.getComment()) + "</body></html>";
  }

  public CommittedChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  public void setTableContextMenu(final ActionGroup group) {
    PopupHandler.installPopupHandler(myChangeListsView, group, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }
}
