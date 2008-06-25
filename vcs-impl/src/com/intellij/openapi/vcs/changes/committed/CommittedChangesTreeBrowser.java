/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeCopyProvider;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ui.TreeWithEmptyText;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class CommittedChangesTreeBrowser extends JPanel implements TypeSafeDataProvider, Disposable, DecoratorManager {
  private static final Object MORE_TAG = new Object();

  private final Project myProject;
  private final TreeWithEmptyText myChangesTree;
  private final RepositoryChangesBrowser myChangesView;
  private List<CommittedChangeList> myChangeLists;
  private List<CommittedChangeList> mySelectedChangeLists;
  private ChangeListGroupingStrategy myGroupingStrategy = ChangeListGroupingStrategy.DATE;
  private ChangeListFilteringStrategy myFilteringStrategy = ChangeListFilteringStrategy.NONE;
  private final Splitter myFilterSplitter;
  private final JPanel myLeftPanel;
  private final JPanel myToolbarPanel;
  private final FilterChangeListener myFilterChangeListener = new FilterChangeListener();
  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsDataImpl();
  private final CopyProvider myCopyProvider;
  private final TreeExpander myTreeExpander;
  private String myHelpId;

  private List<CommittedChangeListDecorator> myDecorators;

  @NonNls public static final String ourHelpId = "reference.changesToolWindow.incoming";

  public CommittedChangesTreeBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(new BorderLayout());

    myProject = project;
    myDecorators = new LinkedList<CommittedChangeListDecorator>();
    myChangeLists = changeLists;
    myChangesTree = new ChangesBrowserTree();
    myChangesTree.setRootVisible(false);
    myChangesTree.setShowsRootHandles(true);
    myChangesTree.setCellRenderer(new CommittedChangeListRenderer(project, myDecorators));
    TreeUtil.expandAll(myChangesTree);

    myChangesView = new RepositoryChangesBrowser(project, changeLists);
    myChangesView.getListPanel().setBorder(null);

    myChangesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    final TreeLinkMouseListener linkMouseListener = new TreeLinkMouseListener(new CommittedChangeListRenderer(project, myDecorators)) {
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
    myToolbarPanel = new JPanel(new BorderLayout());
    myLeftPanel.add(myToolbarPanel, BorderLayout.NORTH);
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
    myTreeExpander = new DefaultTreeExpander(myChangesTree);
    myChangesView.addToolbarAction(ActionManager.getInstance().getAction("Vcs.ShowTabbedFileHistory"));

    myHelpId = ourHelpId;
  }

  private TreeModel buildTreeModel() {
    final List<CommittedChangeList> filteredChangeLists = myFilteringStrategy.filterChangeLists(myChangeLists);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode lastGroupNode = null;
    String lastGroupName = null;
    Collections.sort(filteredChangeLists, myGroupingStrategy.getComparator());
    for(CommittedChangeList list: filteredChangeLists) {
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

  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public void setEmptyText(final String emptyText) {
    myChangesTree.setEmptyText(emptyText);
  }

  public void clearEmptyText() {
    myChangesTree.clearEmptyText();
  }

  public void appendEmptyText(final String text, final SimpleTextAttributes attrs) {
    myChangesTree.appendEmptyText(text, attrs);
  }

  public void appendEmptyText(final String text, final SimpleTextAttributes attrs, ActionListener clickListener) {
    myChangesTree.appendEmptyText(text, attrs, clickListener);
  }

  public void addToolBar(JComponent toolBar) {
    myToolbarPanel.add(toolBar, BorderLayout.NORTH);
  }

  public void addAuxiliaryToolbar(JComponent bar) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(bar, BorderLayout.WEST);
    myToolbarPanel.add(panel, BorderLayout.SOUTH);
  }

  public void dispose() {
    mySplitterProportionsData.saveSplitterProportions(this);
    mySplitterProportionsData.externalizeToDimensionService("CommittedChanges.SplitterProportions");
    myChangesView.dispose();
  }

  public void setItems(@NotNull List<CommittedChangeList> items, final boolean keepFilter, final CommittedChangesBrowserUseCase useCase) {
    myChangesView.setUseCase(useCase);
    myChangeLists = items;
    if (!keepFilter) {
      myFilteringStrategy.setFilterBase(items);
    }
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
    List<CommittedChangeList> selection = new ArrayList<CommittedChangeList>();
    final TreePath[] selectionPaths = myChangesTree.getSelectionPaths();
    if (selectionPaths != null) {
      for(TreePath path: selectionPaths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof CommittedChangeList) {
          selection.add((CommittedChangeList) node.getUserObject());
        }
      }
    }

    if (!selection.equals(mySelectedChangeLists)) {
      mySelectedChangeLists = selection;
      myChangesView.setChangesToDisplay(collectChanges(mySelectedChangeLists, false));
    }
  }

  private static List<Change> collectChanges(final List<CommittedChangeList> selectedChangeLists, final boolean withMovedTrees) {
    List<Change> result = new ArrayList<Change>();
    Collections.sort(selectedChangeLists, new Comparator<CommittedChangeList>() {
      public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
        return o1.getCommitDate().compareTo(o2.getCommitDate());
      }
    });
    for(CommittedChangeList cl: selectedChangeLists) {
      final Collection<Change> changes = withMovedTrees ? cl.getChangesWithMovedTrees() : cl.getChanges();
      for(Change c: changes) {
        addOrReplaceChange(result, c);
      }
    }
    return result;
  }

  private static void addOrReplaceChange(final List<Change> changes, final Change c) {
    final ContentRevision beforeRev = c.getBeforeRevision();
    if (beforeRev != null) {
      for(Change oldChange: changes) {
        ContentRevision rev = oldChange.getAfterRevision();
        if (rev != null && rev.getFile().equals(beforeRev.getFile())) {
          changes.remove(oldChange);
          if (oldChange.getBeforeRevision() != null || c.getAfterRevision() != null) {
            changes.add(new Change(oldChange.getBeforeRevision(), c.getAfterRevision()));
          }
          return;
        }
      }
    }
    changes.add(c);
  }

  private List<CommittedChangeList> getSelectedChangeLists() {
    return TreeUtil.collectSelectedObjectsOfType(myChangesTree, CommittedChangeList.class);
  }

  public void setTableContextMenu(final ActionGroup group, final List<AnAction> auxiliaryActions) {
    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(group);
    for (AnAction action : auxiliaryActions) {
      menuGroup.add(action);
    }
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

  public ActionToolbar createGroupFilterToolbar(final Project project, final ActionGroup leadGroup, @Nullable final ActionGroup tailGroup) {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(leadGroup);
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
    toolbarGroup.add(new ContextHelpAction(myHelpId));
    if (tailGroup != null) {
      toolbarGroup.add(tailGroup);
    }
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true);
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(VcsDataKeys.CHANGES)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), false);
      sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
    }
    else if (key.equals(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), true);
      sink.put(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN, changes.toArray(new Change[changes.size()]));
    }
    else if (key.equals(VcsDataKeys.CHANGE_LISTS)) {
      final List<CommittedChangeList> lists = getSelectedChangeLists();
      if (lists.size() > 0) {
        sink.put(VcsDataKeys.CHANGE_LISTS, lists.toArray(new CommittedChangeList[lists.size()]));
      }
    }
    else if (key.equals(PlatformDataKeys.NAVIGATABLE_ARRAY)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), false);
      Navigatable[] result = ChangesUtil.getNavigatableArray(myProject, ChangesUtil.getFilesFromChanges(changes));
      sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, result);
    }
    else if (key.equals(PlatformDataKeys.HELP_ID)) {
      sink.put(PlatformDataKeys.HELP_ID, myHelpId);
    }
  }

  public TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  public void repaintTree() {
    myChangesTree.revalidate();
    myChangesTree.repaint();
  }

  public void install(final CommittedChangeListDecorator decorator) {
    myDecorators.add(decorator);
    repaintTree();
  }

  public void remove(final CommittedChangeListDecorator decorator) {
    myDecorators.remove(decorator);
    repaintTree();
  }

  public void reportLoadedLists(final CommittedChangeListsListener listener) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        listener.onBeforeStartReport();
        for (CommittedChangeList list : myChangeLists) {
          listener.report(list);
        }
        listener.onAfterEndReport();
      }
    });
  }

  private static class CommittedChangeListRenderer extends ColoredTreeCellRenderer {
    private final DateFormat myDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, Color.blue);
    private final IssueLinkRenderer myRenderer;
    private final List<CommittedChangeListDecorator> myDecorators;

    public CommittedChangeListRenderer(final Project project, final List<CommittedChangeListDecorator> decorators) {
      myRenderer = new IssueLinkRenderer(project, this);
      myDecorators = decorators;
    }

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      if (node.getUserObject() instanceof CommittedChangeList) {
        CommittedChangeList changeList = (CommittedChangeList) node.getUserObject();

        final Container parent = tree.getParent();
        int parentWidth = parent == null ? 100 : parent.getWidth() - 44;
        String date = ", " + myDateFormat.format(changeList.getCommitDate());
        final FontMetrics fontMetrics = tree.getFontMetrics(tree.getFont());
        final FontMetrics boldMetrics = tree.getFontMetrics(tree.getFont().deriveFont(Font.BOLD));
        int size = fontMetrics.stringWidth(date);
        size += boldMetrics.stringWidth(changeList.getCommitterName());

        boolean truncated = false;
        String description = changeList.getName().trim();
        int pos = description.indexOf("\n");
        if (pos >= 0) {
          description = description.substring(0, pos).trim();
          truncated = true;
        }

        for (CommittedChangeListDecorator decorator : myDecorators) {
          final List<Pair<String,SimpleTextAttributes>> pairs = decorator.decorate(changeList);

          if (pairs != null) {
            for (Pair<String, SimpleTextAttributes> pair : pairs) {
              append(pair.first + ' ', pair.second);
            }
          }
        }

        int descMaxWidth = parentWidth - size - 8;
        boolean partial = (changeList instanceof ReceivedChangeList) && ((ReceivedChangeList)changeList).isPartial();
        if (partial) {
          final String partialMarker = VcsBundle.message("committed.changes.partial.list") + " ";
          append(partialMarker, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          descMaxWidth -= boldMetrics.stringWidth(partialMarker);
        }
        int descWidth = fontMetrics.stringWidth(description);
        if (description.length() == 0 && !truncated) {
          append(VcsBundle.message("committed.changes.empty.comment"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
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

  private class ChangesBrowserTree extends TreeWithEmptyText implements TypeSafeDataProvider {
    public ChangesBrowserTree() {
      super(buildTreeModel());
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (key.equals(PlatformDataKeys.COPY_PROVIDER)) {
        sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
      }
      else if (key.equals(PlatformDataKeys.TREE_EXPANDER)) {
        sink.put(PlatformDataKeys.TREE_EXPANDER, myTreeExpander);
      }
    }
  }
}
