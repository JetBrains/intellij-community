package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class ChangesListView extends TreeTable implements DataProvider {
  private static final ColumnInfo<DefaultMutableTreeNode, String> CHANGELIST_OR_FILE =
    new ColumnInfo<DefaultMutableTreeNode, String>("change") {
      public Class getColumnClass() {
        return TreeTableModel.class;
      }

      public String valueOf(final DefaultMutableTreeNode node) {
        Object object = node.getUserObject();
        if (object instanceof ChangeList) {
          return ((ChangeList)object).getDescription();
        }

        if (object instanceof Change) {
          return getFilePath((Change)object).getName();
        }

        return "";
      }
    };

  private static final ColumnInfo<DefaultMutableTreeNode, String> CHANGE_PATH = new ColumnInfo<DefaultMutableTreeNode, String>("path") {
    public String valueOf(final DefaultMutableTreeNode node) {
      Object object = node.getUserObject();
      if (object instanceof Change) {
        return getFilePath((Change)object).getPath();
      }

      return "";
    }
  };

  private static final ColumnInfo<DefaultMutableTreeNode, String> CHANGE_TYPE = new ColumnInfo<DefaultMutableTreeNode, String>("type") {
    public String valueOf(final DefaultMutableTreeNode node) {
      Object object = node.getUserObject();
      if (object instanceof Change) {
        return ((Change)object).getType().name();
      }

      return "";
    }
  };

  private static FilePath getFilePath(final Change change) {
    ContentRevision revision = change.getBeforeRevision();
    if (revision == null) revision = change.getAfterRevision();

    return revision.getFile();
  }

  private DefaultMutableTreeNode myRoot;

  public ChangesListView() {
    super(
      new ListTreeTableModelOnColumns(new DefaultMutableTreeNode("root"), new ColumnInfo[]{CHANGELIST_OR_FILE, CHANGE_PATH, CHANGE_TYPE}));
    final Tree tree = getTree();
    myRoot = (DefaultMutableTreeNode)tree.getModel().getRoot();
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        Object object = node.getUserObject();
        if (object instanceof ChangeList) {
          final ChangeList list = ((ChangeList)object);
          append(list.getDescription(), list.isDefault()
                                        ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                        : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        }
        else if (object instanceof Change) {
          final Change change = (Change)object;
          final FilePath filePath = getFilePath(change);
          append(filePath.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor(change), null));
          setIcon(filePath.getFileType().getIcon());
        }
      }

      // TODO: take real statuses like merged into an account
      private Color getColor(final Change change) {
        final Change.Type type = change.getType();
        switch (type) {
          case DELETED:
            return FileStatus.COLOR_MISSING;
          case MODIFICATION:
            return FileStatus.COLOR_MODIFIED;
          case MOVED:
            return FileStatus.COLOR_MODIFIED;
          case NEW:
            return FileStatus.COLOR_ADDED;
        }

        return Color.black;
      }
    });
  }

  public void updateModel(java.util.List<ChangeList> changeLists) {
    myRoot.removeAllChildren();
    for (ChangeList list : changeLists) {
      DefaultMutableTreeNode listNode = new DefaultMutableTreeNode(list);
      myRoot.add(listNode);
      for (Change change : list.getChanges()) {
        listNode.add(new DefaultMutableTreeNode(change));
      }
    }

    ((DefaultTreeModel)getTree().getModel()).nodeStructureChanged(myRoot);

    TreeUtil.expandAll(getTree());
  }

  @Nullable
  public Object getData(String dataId) {
    if (DataConstants.CHANGES.equals(dataId)) {
      return getSelectedChanges();
    }
    else if (DataConstants.CHANGE_LISTS.equals(dataId)) {
      return getSelectedChangeLists();
    }
    return null;
  }

  @NotNull
  private Change[] getSelectedChanges() {
    Set<Change> changes = new HashSet<Change>();

    for (ChangeList list : getSelectedChangeLists()) {
      changes.addAll(list.getChanges());
    }

    final TreePath[] paths = getTree().getSelectionPaths();
    if (paths == null) return new Change[0];
    for (TreePath path : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof Change) {
        changes.add((Change)userObject);
      }
    }

    return changes.toArray(new Change[changes.size()]);
  }

  @NotNull
  private ChangeList[] getSelectedChangeLists() {
    Set<ChangeList> lists = new HashSet<ChangeList>();

    final TreePath[] paths = getTree().getSelectionPaths();
    if (paths == null) return new ChangeList[0];

    for (TreePath path : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof ChangeList) {
        lists.add((ChangeList)userObject);
      }
    }

    return lists.toArray(new ChangeList[lists.size()]);
  }
}
