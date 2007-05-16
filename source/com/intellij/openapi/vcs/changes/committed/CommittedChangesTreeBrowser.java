/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeCopyProvider;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CommittedChangesTreeBrowser extends JPanel implements TypeSafeDataProvider, Disposable {
  private static final Object MORE_TAG = new Object();

  private final Tree myChangesTree;
  private final ChangesBrowser myChangesView;
  private List<CommittedChangeList> myChangeLists;
  private CommittedChangeList mySelectedChangeList;
  private ChangeListGroupingStrategy myGroupingStrategy = ChangeListGroupingStrategy.DATE;
  private ChangeListFilteringStrategy myFilteringStrategy = ChangeListFilteringStrategy.NONE;
  private Splitter myFilterSplitter;
  private JPanel myLeftPanel;
  private CommittedChangeListRenderer myCellRenderer;
  private FilterChangeListener myFilterChangeListener = new FilterChangeListener();
  private List<CommittedChangeList> myFilteredChangeLists;
  private final SplitterProportionsData mySplitterProportionsData = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();
  private CopyProvider myCopyProvider;

  public CommittedChangesTreeBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(new BorderLayout());

    myChangeLists = changeLists;
    myChangesTree = new Tree(buildTreeModel()) {
      @Override
      public boolean getScrollableTracksViewportWidth() {
        return true;
      }
    };
    myChangesTree.setRootVisible(false);
    myChangesTree.setShowsRootHandles(true);
    myCellRenderer = new CommittedChangeListRenderer(project);
    myChangesTree.setCellRenderer(myCellRenderer);
    TreeUtil.expandAll(myChangesTree);

    myChangesView = new RepositoryChangesBrowser(project, changeLists);
    myChangesView.getListPanel().setBorder(null);

    myChangesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    final TreeLinkMouseListener linkMouseListener = new TreeLinkMouseListener(new CommittedChangeListRenderer(project)) {
      @Override
      protected void handleTagClick(final Object tag) {
        if (tag == MORE_TAG) {
          ChangeListDetailsAction.showDetailsPopup(project, (CommittedChangeList) myLastHitNode.getUserObject());
        }
        else {
          super.handleTagClick(tag);
        }
      }
    };
    linkMouseListener.install(myChangesTree);

    myLeftPanel = new JPanel(new BorderLayout());
    myFilterSplitter = new Splitter(false, 0.5f);
    myFilterSplitter.setSecondComponent(new JScrollPane(myChangesTree));
    myLeftPanel.add(myFilterSplitter, BorderLayout.CENTER);
    final Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(myLeftPanel);
    splitter.setSecondComponent(myChangesView);

    add(splitter, BorderLayout.CENTER);

    mySplitterProportionsData.externalizeFromDimensionService("CommittedChanges.SplitterProportions");
    mySplitterProportionsData.restoreSplitterProportions(this);

    updateBySelectionChange();

    ActionManager.getInstance().getAction("CommittedChanges.Details").registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_QUICK_JAVADOC)),
      this);

    myCopyProvider = new TreeCopyProvider(myChangesTree);
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
    mySplitterProportionsData.saveSplitterProportions(this);
    mySplitterProportionsData.externalizeToDimensionService("CommittedChanges.SplitterProportions");
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
    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(group);
    menuGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    PopupHandler.installPopupHandler(myChangesTree, menuGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
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

  public ActionToolbar createGroupFilterToolbar(final Project project, final ActionGroup group) {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(group);
    toolbarGroup.addSeparator();
    toolbarGroup.add(new SelectFilteringAction(project, this));
    toolbarGroup.add(new SelectGroupingAction(this));
    final ExpandAllAction expandAllAction = new ExpandAllAction(myChangesTree);
    final CollapseAllAction collapseAllAction = new CollapseAllAction(myChangesTree);
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myChangesTree);
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myChangesTree);
    toolbarGroup.add(expandAllAction);
    toolbarGroup.add(collapseAllAction);
    toolbarGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true);
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(DataKeys.CHANGES)) {
      final CommittedChangeList list = getSelectedChangeList();
      if (list != null) {
        final Collection<Change> changes = list.getChanges();
        sink.put(DataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
      }
    }
    else if (key.equals(DataKeys.CHANGE_LISTS)) {
      final CommittedChangeList list = getSelectedChangeList();
      if (list != null) {
        sink.put(DataKeys.CHANGE_LISTS, new ChangeList[] { list });
      }
    }
    else if (key.equals(DataKeys.COPY_PROVIDER)) {
      sink.put(DataKeys.COPY_PROVIDER, myCopyProvider);
    }
  }

  private static class CommittedChangeListRenderer extends ColoredTreeCellRenderer {
    private DateFormat myDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, Color.blue);
    private IssueLinkRenderer myRenderer;

    public CommittedChangeListRenderer(final Project project) {
      myRenderer = new IssueLinkRenderer(project, this);
    }

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      if (node.getUserObject() instanceof CommittedChangeList) {
        CommittedChangeList changeList = (CommittedChangeList) node.getUserObject();

        int parentWidth = tree.getParent().getWidth() - 44;
        String date = ", " + myDateFormat.format(changeList.getCommitDate());
        final FontMetrics fontMetrics = tree.getFontMetrics(tree.getFont());
        int size = fontMetrics.stringWidth(date);
        size += tree.getFontMetrics(tree.getFont().deriveFont(Font.BOLD)).stringWidth(changeList.getCommitterName());

        boolean truncated = false;
        String description = changeList.getName().trim();
        int pos = description.indexOf("\n");
        if (pos >= 0) {
          description = description.substring(0, pos).trim();
          truncated = true;
        }

        int descWidth = fontMetrics.stringWidth(description);
        final int descMaxWidth = parentWidth - size - 8;
        if (description.length() == 0 && !truncated) {
          append("<no comment>", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          appendAlign(parentWidth - size);
        }
        else if (descMaxWidth < 0) {
          myRenderer.appendTextWithLinks(description);
        }
        else if (descWidth < descMaxWidth && !truncated) {
          myRenderer.appendTextWithLinks(description);
          appendAlign(parentWidth - size);
        }
        else {
          final String moreMarker = VcsBundle.message("changes.browser.details.marker");
          int moreWidth = fontMetrics.stringWidth(moreMarker);
          while(description.length() > 0 && descWidth + moreWidth > descMaxWidth) {
            description = trimLastWord(description);
            descWidth = fontMetrics.stringWidth(description + " ");
          }
          myRenderer.appendTextWithLinks(description);
          append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          append(moreMarker, LINK_ATTRIBUTES, MORE_TAG);
          appendAlign(parentWidth - size);
        }

        append(changeList.getCommitterName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append(date, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else if (node.getUserObject() != null) {
        append(node.getUserObject().toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    private static String trimLastWord(final String description) {
      int pos = description.trim().lastIndexOf(' ');
      if (pos >= 0) {
        return description.substring(0, pos).trim();
      }
      return description.substring(0, description.length()-1);
    }

    public Dimension getPreferredSize() {
      return new Dimension(2000, super.getPreferredSize().height);
    }
  }

  private class FilterChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      updateModel();
    }
  }

}
