/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CommittedChangesTreeBrowser extends JPanel {
  private final Tree myChangesTree;
  private final ChangesBrowser myChangesView;
  private List<CommittedChangeList> myChangeLists;
  private CommittedChangeList mySelectedChangeList;
  private ChangeListGroupingStrategy myGroupingStrategy = ChangeListGroupingStrategy.DATE;
  private ChangeListFilteringStrategy myFilteringStrategy = ChangeListFilteringStrategy.NONE;
  private Splitter myFilterSplitter;
  private JPanel myLeftPanel;
  private CommittedChangeListRenderer myCellRenderer;
  private JScrollPane myChangesTreeScrollPane;
  private Splitter mySplitter;
  private FilterChangeListener myFilterChangeListener = new FilterChangeListener();
  private List<CommittedChangeList> myFilteredChangeLists;

  public CommittedChangesTreeBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(new BorderLayout());

    myChangeLists = changeLists;
    myChangesTree = new Tree(buildTreeModel());
    myChangesTree.setRootVisible(false);
    myChangesTree.setShowsRootHandles(true);
    myCellRenderer = new CommittedChangeListRenderer();
    myChangesTree.setCellRenderer(myCellRenderer);
    TreeUtil.expandAll(myChangesTree);

    myChangesView = new ChangesBrowser(project, changeLists, Collections.<Change>emptyList(), null, false, false);
    myChangesView.getListPanel().setBorder(null);

    OpenRepositoryVersionAction action = new OpenRepositoryVersionAction();
    action.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    myChangesView.addToolbarAction(action);

    myChangesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateBySelectionChange();
      }
    });
    myChangesTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        final TreePath path = myChangesTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          final Rectangle rectangle = myChangesTree.getPathBounds(path);
          int dx = e.getX() - rectangle.x;
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
          myCellRenderer.customizeCellRenderer(myChangesTree, treeNode, false, false, false, -1, false);
          int i = myCellRenderer.findFragmentAt(dx);
          if (i >= 0) {
            String text = myCellRenderer.getFragmentText(i);
            if (text.equals(VcsBundle.message("changes.browser.details.marker"))) {
              ChangeListDetailsAction.showDetailsPopup(project, (CommittedChangeList) treeNode.getUserObject());
            }
          }
        }
      }
    });

    myLeftPanel = new JPanel(new BorderLayout());
    myChangesTreeScrollPane = new JScrollPane(myChangesTree);
    myFilterSplitter = new Splitter(false, 0.5f);
    myFilterSplitter.setSecondComponent(myChangesTreeScrollPane);
    myLeftPanel.add(myFilterSplitter, BorderLayout.CENTER);
    mySplitter = new Splitter(false, 0.7f);
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myChangesView);

    add(mySplitter, BorderLayout.CENTER);

    updateBySelectionChange();

    new ChangeListDetailsAction().registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_QUICK_JAVADOC)),
      this);
  }

  private TreeModel buildTreeModel() {
    myFilteredChangeLists = myFilteringStrategy.filterChangeLists(myChangeLists);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode lastGroupNode = null;
    String lastGroupName = null;
    Collections.sort(myFilteredChangeLists, myGroupingStrategy.getComparator());
    for(CommittedChangeList list: myFilteredChangeLists) {
      String groupName = myGroupingStrategy.getGroupName(list);
      if (!Comparing.equal(groupName, lastGroupName)) {
        lastGroupName = groupName;
        lastGroupNode = new DefaultMutableTreeNode(lastGroupName);
        root.add(lastGroupNode);
      }
      assert lastGroupNode != null;
      lastGroupNode.add(new DefaultMutableTreeNode(list));
    }
    return model;
  }

  public void addToolBar(JComponent toolBar) {
    myLeftPanel.add(toolBar, BorderLayout.NORTH);
  }

  public void dispose() {
    myChangesView.dispose();
  }

  public void setItems(List<CommittedChangeList> items) {
    myChangeLists = items;
    myFilteringStrategy.setFilterBase(items);
    updateModel();
  }

  private void updateModel() {
    myChangesTree.setModel(buildTreeModel());
    TreeUtil.expandAll(myChangesTree);
  }

  public void setGroupingStrategy(ChangeListGroupingStrategy strategy) {
    myGroupingStrategy = strategy;
    updateModel();
  }

  private void updateBySelectionChange() {
    CommittedChangeList list = null;
    final TreePath selectionPath = myChangesTree.getSelectionPath();
    if (selectionPath != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
      if (node.getUserObject() instanceof CommittedChangeList) {
        list = (CommittedChangeList) node.getUserObject();
      }
    }
    if (list != mySelectedChangeList) {
      mySelectedChangeList = list;
      myChangesView.setChangesToDisplay(list != null ? new ArrayList<Change>(list.getChanges()) : Collections.<Change>emptyList());
    }
  }

  public CommittedChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  public void setTableContextMenu(final ActionGroup group) {
    PopupHandler.installPopupHandler(myChangesTree, group, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  public void setFilteringStrategy(final ChangeListFilteringStrategy filteringStrategy) {
    if (myFilteringStrategy == filteringStrategy) return;
    myFilteringStrategy.removeChangeListener(myFilterChangeListener);
    myFilteringStrategy = filteringStrategy;
    boolean wasEmpty = (myFilterSplitter.getFirstComponent() == null);
    final JComponent filterUI = myFilteringStrategy.getFilterUI();
    myFilterSplitter.setFirstComponent(filterUI);
    myFilteringStrategy.setFilterBase(myChangeLists);
    myFilteringStrategy.addChangeListener(myFilterChangeListener);
    if (wasEmpty && filterUI != null) {
      myFilterSplitter.setProportion(0.25f);
    }
    myFilterSplitter.doLayout();
    updateModel();
  }

  private static class CommittedChangeListRenderer extends ColoredTreeCellRenderer {
    private DateFormat myDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      if (node.getUserObject() instanceof CommittedChangeList) {
        CommittedChangeList changeList = (CommittedChangeList) node.getUserObject();

        boolean truncated = false;
        String description = changeList.getName();
        int pos = description.indexOf("\n");
        if (pos >= 0) {
          description = description.substring(0, pos).trim();
          truncated = true;
        }
        append(description, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
        if (truncated) {
          append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          append(VcsBundle.message("changes.browser.details.marker"), new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, Color.blue));
        }
        append(" - ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        append(changeList.getCommitterName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append(" at ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        append(myDateFormat.format(changeList.getCommitDate()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else if (node.getUserObject() != null) {
        append(node.getUserObject().toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }
  }

  private class FilterChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      updateModel();
    }
  }
}
