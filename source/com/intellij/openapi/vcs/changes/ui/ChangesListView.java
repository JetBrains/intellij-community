package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.*;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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
  private ChangesListView.DragSource myDragSource;
  private ChangesListView.DropTarget myDropTarget;
  private DnDManager myDndManager;
  private ChangeListOwner myDragOwner;
  private final Project myProject;

  private static FilePath getFilePath(final Change change) {
    ContentRevision revision = change.getBeforeRevision();
    if (revision == null) revision = change.getAfterRevision();

    return revision.getFile();
  }

  private DefaultMutableTreeNode myRoot;

  public ChangesListView(final Project project) {
    super(
      new ListTreeTableModelOnColumns(new DefaultMutableTreeNode("root"), new ColumnInfo[]{CHANGELIST_OR_FILE, CHANGE_PATH, CHANGE_TYPE}));
    myProject = project;
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

  public void installDndSupport(ChangeListOwner owner) {
    myDragOwner = owner;
    myDragSource = new DragSource();
    myDropTarget = new DropTarget();
    myDndManager = DnDManager.getInstance(myProject);

    myDndManager.registerSource(myDragSource, this);
    myDndManager.registerTarget(myDropTarget, this);
  }

  public void dispose() {
    if (myDragSource != null) {
      myDndManager.unregisterSource(myDragSource, getTree());
      myDndManager.unregisterTarget(myDropTarget, getTree());

      myDragSource = null;
      myDropTarget = null;
      myDndManager = null;
      myDragOwner = null;
    }
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
    else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
      return getSelectedFiles();
    }
    else if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      final VirtualFile[] files = getSelectedFiles();
      Navigatable[] navigatables = new Navigatable[files.length];
      for (int i = 0; i < files.length; i++) {
        navigatables[i] = new OpenFileDescriptor(myProject, files[i], 0);
      }
      return navigatables;
    }

    return null;
  }

  private VirtualFile[] getSelectedFiles() {
    final Change[] changes = getSelectedChanges();
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile file = afterRevision.getFile().getVirtualFile();
        if (file != null && file.isValid()) {
          files.add(file);
        }
      }
    }
    return files.toArray(new VirtualFile[files.size()]);
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

  public class DragSource implements DnDSource {
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      if (action != DnDAction.MOVE) return false;
      return getSelectedChanges().length > 0;
    }

    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      return new DnDDragStartBean(new ChangeListDragBean(ChangesListView.this, getSelectedChanges()));
    }

    @Nullable
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      return new Pair<Image, Point>(DragImageFactory.createImage(ChangesListView.this, 0), new Point());
    }
  }

  private static class DragImageFactory {
    private static void drawSelection(JTable table, int column, Graphics g, final int width) {
      int y = 0;
      final int[] rows = table.getSelectedRows();
      final int height = table.getRowHeight();
      for (int row : rows) {
        final TableCellRenderer renderer = table.getCellRenderer(row, column);
        final Component component = renderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
        g.translate(0, y);
        component.setBounds(0, 0, width, height);
        boolean wasOpaque = false;
        if (component instanceof JComponent) {
          final JComponent j = (JComponent)component;
          if (j.isOpaque()) wasOpaque = true;
          j.setOpaque(false);
        }
        component.paint(g);
        if (wasOpaque) {
          ((JComponent)component).setOpaque(true);
        }
        y += height;
        g.translate(0, -y);
      }
    }

    public static Image createImage(final JTable table, int column) {
      final int height = Math.min(100, table.getSelectedRowCount() * table.getRowHeight());
      final int width = table.getColumnModel().getColumn(column).getWidth();

      final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

      drawSelection(table, column, g2, width);
      return image;
    }
  }

  private static class ChangeListDragBean {
    private ChangesListView myView;
    private Change[] myChanges;
    private ChangeList myDropList = null;


    public ChangeListDragBean(final ChangesListView view, final Change[] changes) {
      myView = view;
      myChanges = changes;
    }

    public ChangesListView getView() {
      return myView;
    }

    public Change[] getChanges() {
      return myChanges;
    }

    public void setTargetList(final ChangeList dropList) {
      myDropList = dropList;
    }


    public ChangeList getDropList() {
      return myDropList;
    }
  }

  public class DropTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.hideHighlighter();

      Object attached = aEvent.getAttachedObject();
      if (!(attached instanceof ChangeListDragBean)) return false;

      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      if (dragBean.getView() != ChangesListView.this) return false;
      dragBean.setTargetList(null);

      RelativePoint dropPoint = aEvent.getRelativePoint();
      Point onTable = dropPoint.getPoint(ChangesListView.this);
      final int dropRow = rowAtPoint(onTable);
      final TreePath dropPath = getTree().getPathForRow(dropRow);

      if (dropPath == null) return false;

      Object object;
      DefaultMutableTreeNode dropNode = (DefaultMutableTreeNode)dropPath.getLastPathComponent();
      do {
        if (dropNode == null || dropNode.isRoot()) return false;
        object = dropNode.getUserObject();
        if (object instanceof ChangeList) break;
        dropNode = (DefaultMutableTreeNode)dropNode.getParent();
      }
      while (true);

      ChangeList dropList = (ChangeList)object;
      final Change[] changes = dragBean.getChanges();
      for (Change change : dropList.getChanges()) {
        for (Change incomingChange : changes) {
          if (change == incomingChange) return false;
        }
      }

      final Rectangle tableCellRect = getCellRect(getTree().getRowForPath(new TreePath(dropNode.getPath())), 0, false);

      aEvent.setHighlighting(new RelativeRectangle(ChangesListView.this, tableCellRect),
                             DnDEvent.DropTargetHighlightingType.RECTANGLE);

      aEvent.setDropPossible(true, null);
      dragBean.setTargetList(dropList);

      return false;
    }

    public void drop(DnDEvent aEvent) {
      Object attached = aEvent.getAttachedObject();
      if (!(attached instanceof ChangeListDragBean)) return;

      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      final ChangeList dropList = dragBean.getDropList();
      if (dropList != null) {
        myDragOwner.moveChangesTo(dropList, dragBean.getChanges());
      }
    }

    public void cleanUpOnLeave() {
    }

    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }
  }
}
