package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.*;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class ChangesListView extends Tree implements DataProvider {
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

  private FileStatus getChangeStatus(Change change) {
    final VirtualFile vFile = getFilePath(change).getVirtualFile();
    if (vFile == null) return FileStatus.DELETED;
    return FileStatusManager.getInstance(myProject).getStatus(vFile);
  }

  private DefaultMutableTreeNode myRoot;

  public ChangesListView(final Project project) {
    myProject = project;
    myRoot = new DefaultMutableTreeNode("root");
    setModel(new DefaultTreeModel(myRoot));

    setShowsRootHandles(true);
    setRootVisible(false);
    setCellRenderer(new ColoredTreeCellRenderer() {
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
          append(" (" + filePath.getVirtualFileParent().getPresentableUrl() + ", " + getChangeStatus(change).getText() + ")",
                 SimpleTextAttributes.GRAYED_ATTRIBUTES);
          setIcon(filePath.getFileType().getIcon());
        }
        else if (object instanceof VirtualFile) {
          final VirtualFile file = (VirtualFile)object;
          append(file.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.COLOR_UNKNOWN));
          final VirtualFile parentFile = file.getParent();
          assert parentFile != null;
          append(" (" + parentFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          setIcon(file.getFileType().getIcon());
        }
        else {
          append(object.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

      private Color getColor(final Change change) {
        return getChangeStatus(change).getColor();
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
      myDndManager.unregisterSource(myDragSource, this);
      myDndManager.unregisterTarget(myDropTarget, this);

      myDragSource = null;
      myDropTarget = null;
      myDndManager = null;
      myDragOwner = null;
    }
  }

  public void updateModel(java.util.List<ChangeList> changeLists, java.util.List<VirtualFile> unversionedFiles) {
    myRoot.removeAllChildren();
    for (ChangeList list : changeLists) {
      DefaultMutableTreeNode listNode = new DefaultMutableTreeNode(list);
      myRoot.add(listNode);
      for (Change change : list.getChanges()) {
        listNode.add(new DefaultMutableTreeNode(change));
      }
    }

    if (!unversionedFiles.isEmpty()) {
      DefaultMutableTreeNode unversionedNode = new DefaultMutableTreeNode("Unversioned Files");
      myRoot.add(unversionedNode);
      for (VirtualFile file : unversionedFiles) {
        unversionedNode.add(new DefaultMutableTreeNode(file));
      }
    }

    ((DefaultTreeModel)getModel()).nodeStructureChanged(myRoot);

    TreeUtil.expandAll(this);
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

    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject instanceof VirtualFile) {
          final VirtualFile file = (VirtualFile)userObject;
          if (file.isValid()) {
            files.add(file);
          }
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

    final TreePath[] paths = getSelectionPaths();
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

    final TreePath[] paths = getSelectionPaths();
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

  public void setMenuActions(final ActionGroup menuGroup) {
    PopupHandler.installUnknownPopupHandler(this, menuGroup, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(this);
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
      return new Pair<Image, Point>(DragImageFactory.createImage(ChangesListView.this), new Point());
    }

    public void dragDropEnd() {
    }
  }

  private static class DragImageFactory {
    private DragImageFactory() {}

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

    private static void drawSelection(JTree tree, Graphics g, final int width) {
      int y = 0;
      final int[] rows = tree.getSelectionRows();
      final int height = tree.getRowHeight();
      for (int row : rows) {
        final TreeCellRenderer renderer = tree.getCellRenderer();
        final Object value = tree.getPathForRow(row).getLastPathComponent();
        if (value == null) continue;
        final Component component = renderer.getTreeCellRendererComponent(tree, value, false, false, false, row, false);
        if (component.getFont() == null) {
          component.setFont(new JLabel().getFont()); // TODO: ??? Something strange happens here. When painted renderer's component has null font
        }
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

    public static Image createImage(final JTree tree) {
      final int height = Math.min(100, tree.getSelectionCount() * tree.getRowHeight());
      final int width = tree.getWidth();

      final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

      drawSelection(tree, g2, width);
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
      Point onTree = dropPoint.getPoint(ChangesListView.this);
      final TreePath dropPath = getPathForLocation(onTree.x, onTree.y);

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

      final Rectangle tableCellRect = getPathBounds(new TreePath(dropNode.getPath()));

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
