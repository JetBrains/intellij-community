package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.dnd.*;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeUtils;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class ChangesListView extends Tree implements DataProvider, DeleteProvider, AdvancedDnDSource {
  private ChangesListView.DropTarget myDropTarget;
  private DnDManager myDndManager;
  private ChangeListOwner myDragOwner;
  private final Project myProject;
  private TreeState myTreeState;
  private boolean myShowFlatten = false;

  @NonNls public static final String UNVERSIONED_FILES_KEY = "ChangeListView.UnversionedFiles";
  @NonNls public static final String MISSING_FILES_KEY = "ChangeListView.MissingFiles";

  public ChangesListView(final Project project) {
    myProject = project;

    getModel().setRoot(new ChangesBrowserNode(TreeModelBuilder.ROOT_NODE_VALUE));

    setShowsRootHandles(true);
    setRootVisible(false);

    new TreeSpeedSearch(this, new NodeToTextConvertor());
    SmartExpander.installOn(this);
  }

  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  public static FilePath safeCastToFilePath(Object o) {
    if (o instanceof FilePath) return (FilePath)o;
    return null;
  }

  public synchronized void addMouseListener(MouseListener l) {
    super.addMouseListener(l);    //To change body of overridden methods use File | Settings | File Templates.
  }


  public static String getRelativePath(FilePath parent, FilePath child) {
    if (parent == null) return child.getPath().replace('/', File.separatorChar);
    return child.getPath().substring(parent.getPath().length() + 1).replace('/', File.separatorChar);
  }

  public void installDndSupport(ChangeListOwner owner) {
    myDragOwner = owner;
    myDropTarget = new DropTarget();
    myDndManager = DnDManager.getInstance(myProject);

    myDndManager.registerSource(this);
    myDndManager.registerTarget(myDropTarget, this);
  }

  public void dispose() {
    if (myDropTarget != null) {
      myDndManager.unregisterSource(this);
      myDndManager.unregisterTarget(myDropTarget, this);

      myDropTarget = null;
      myDndManager = null;
      myDragOwner = null;
    }
  }

  private void storeState() {
    myTreeState = TreeState.createOn(this, (ChangesBrowserNode)getModel().getRoot());
  }

  private void restoreState() {
    myTreeState.applyTo(this, (ChangesBrowserNode)getModel().getRoot());
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setShowFlatten(final boolean showFlatten) {
    myShowFlatten = showFlatten;
  }

  public void updateModel(List<LocalChangeList> changeLists, List<VirtualFile> unversionedFiles, final List<File> locallyDeletedFiles) {
    storeState();

    TreeModelBuilder builder = new TreeModelBuilder(myProject, isShowFlatten());
    final DefaultTreeModel model = builder.buildModel(changeLists, unversionedFiles, locallyDeletedFiles);
    setModel(model);
    setCellRenderer(new ChangeBrowserNodeRenderer(myProject, isShowFlatten()));

    expandPath(new TreePath(((ChangesBrowserNode)model.getRoot()).getPath()));

    restoreState();
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
    else if (DataConstants.NAVIGATABLE.equals(dataId)) {
      final VirtualFile[] files = getSelectedFiles();
      if (files.length == 1) {
        return new OpenFileDescriptor(myProject, files[0], 0);
      }
    }
    else if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      return ChangesUtil.getNavigatableArray(myProject, getSelectedFiles());
    }
    else if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return this;
    }
    else if (UNVERSIONED_FILES_KEY.equals(dataId)) {
      return getSelectedUnversionedFiles();
    }
    else if (MISSING_FILES_KEY.equals(dataId)) {
      return getSelectedMissingFiles();
    }

    return null;
  }

  private List<VirtualFile> getSelectedUnversionedFiles() {
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
        files.addAll(node.getAllFilesUnder());
      }
    }
    return files;
  }

  private List<File> getSelectedMissingFiles() {
    List<File> files = new ArrayList<File>();
    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
        files.addAll(node.getAllIOFilesUnder());
      }
    }
    return files;
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

    files.addAll(getSelectedUnversionedFiles());

    return files.toArray(new VirtualFile[files.size()]);
  }

  public void deleteElement(DataContext dataContext) {
    PsiElement[] elements = getPsiElements(dataContext);
    DeleteHandler.deletePsiElement(elements, myProject);
  }

  public boolean canDeleteElement(DataContext dataContext) {
    PsiElement[] elements = getPsiElements(dataContext);
    return DeleteHandler.shouldEnableDeleteAction(elements);
  }

  private PsiElement[] getPsiElements(final DataContext dataContext) {
    List<PsiElement> elements = new ArrayList<PsiElement>();
    final PsiManager manager = PsiManager.getInstance(myProject);
    List<VirtualFile> files = (List<VirtualFile>)dataContext.getData(UNVERSIONED_FILES_KEY);
    if (files == null) return PsiElement.EMPTY_ARRAY;

    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        final PsiDirectory psiDir = manager.findDirectory(file);
        if (psiDir != null) {
          elements.add(psiDir);
        }
      }
      else {
        final PsiFile psiFile = manager.findFile(file);
        if (psiFile != null) {
          elements.add(psiFile);
        }
      }
    }
    return elements.toArray(new PsiElement[elements.size()]);
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
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      changes.addAll(node.getAllChangesUnder());
    }

    return changes.toArray(new Change[changes.size()]);
  }

  @NotNull
  private ChangeList[] getSelectedChangeLists() {
    Set<ChangeList> lists = new HashSet<ChangeList>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new ChangeList[0];

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof ChangeList) {
        lists.add((ChangeList)userObject);
      }
    }

    return lists.toArray(new ChangeList[lists.size()]);
  }

  public void setMenuActions(final ActionGroup menuGroup) {
    PopupHandler.installPopupHandler(this, menuGroup, ActionPlaces.CHANGES_VIEW, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(this);
  }

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
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
          component.setFont(tree.getFont());
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
      final int height = Math.max(20, Math.min(100, table.getSelectedRowCount() * table.getRowHeight()));
      final int width = table.getColumnModel().getColumn(column).getWidth();

      final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

      drawSelection(table, column, g2, width);
      return image;
    }

    public static Image createImage(final JTree tree) {
      final TreeSelectionModel model = tree.getSelectionModel();
      final TreePath[] paths = model.getSelectionPaths();

      int count = 0;
      final List<ChangesBrowserNode> nodes = new ArrayList<ChangesBrowserNode>();
      for (final TreePath path : paths) {
        final ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
        if (!node.isLeaf()) {
          nodes.add(node);
          count += node.getCount();
        }
      }

      for (TreePath path : paths) {
        final ChangesBrowserNode element = (ChangesBrowserNode)path.getLastPathComponent();
        boolean child = false;
        for (final ChangesBrowserNode node : nodes) {
          if (node.isNodeChild(element)) {
            child = true;
            break;
          }
        }

        if (!child) {
          if (element.isLeaf()) count++;
        } else if (!element.isLeaf()) {
          count -= element.getCount();
        }
      }

      final JLabel label = new JLabel(VcsBundle.message("changes.view.dnd.label", count));
      label.setOpaque(true);
      label.setForeground(tree.getForeground());
      label.setBackground(tree.getBackground());
      label.setFont(tree.getFont());
      label.setSize(label.getPreferredSize());
      final BufferedImage image = new BufferedImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2 = (Graphics2D)image.getGraphics();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      label.paint(g2);
      g2.dispose();

      return image;
    }


  }

  private static class ChangeListDragBean {
    private ChangesListView myView;
    private Change[] myChanges;
    private List<VirtualFile> myUnversionedFiles;
    private LocalChangeList myDropList = null;

    public ChangeListDragBean(final ChangesListView view, final Change[] changes, final List<VirtualFile> unversionedFiles) {
      myView = view;
      myChanges = changes;
      myUnversionedFiles = unversionedFiles;
    }

    public ChangesListView getView() {
      return myView;
    }

    public Change[] getChanges() {
      return myChanges;
    }

    public List<VirtualFile> getUnversionedFiles() {
      return myUnversionedFiles;
    }

    public void setTargetList(final LocalChangeList dropList) {
      myDropList = dropList;
    }

    public LocalChangeList getDropList() {
      return myDropList;
    }
  }

  public class DropTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.hideHighlighter();
      aEvent.setDropPossible(false, "");

      Object attached = aEvent.getAttachedObject();
      if (!(attached instanceof ChangeListDragBean)) return false;

      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      if (dragBean.getView() != ChangesListView.this) return false;
      if (dragBean.getChanges().length == 0 && dragBean.getUnversionedFiles().size() == 0) return false;
      dragBean.setTargetList(null);

      RelativePoint dropPoint = aEvent.getRelativePoint();
      Point onTree = dropPoint.getPoint(ChangesListView.this);
      final TreePath dropPath = getPathForLocation(onTree.x, onTree.y);

      if (dropPath == null) return false;

      Object object;
      ChangesBrowserNode dropNode = (ChangesBrowserNode)dropPath.getLastPathComponent();
      do {
        if (dropNode == null || dropNode.isRoot()) return false;
        object = dropNode.getUserObject();
        if (object instanceof ChangeList) break;
        dropNode = (ChangesBrowserNode)dropNode.getParent();
      }
      while (true);

      LocalChangeList dropList = (LocalChangeList)object;
      final Change[] changes = dragBean.getChanges();
      for (Change change : dropList.getChanges()) {
        for (Change incomingChange : changes) {
          if (change == incomingChange) return false;
        }
      }

      final Rectangle tableCellRect = getPathBounds(new TreePath(dropNode.getPath()));

      aEvent.setHighlighting(new RelativeRectangle(ChangesListView.this, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);

      aEvent.setDropPossible(true, null);
      dragBean.setTargetList(dropList);

      return false;
    }

    public void drop(DnDEvent aEvent) {
      Object attached = aEvent.getAttachedObject();
      if (!(attached instanceof ChangeListDragBean)) return;

      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      final LocalChangeList dropList = dragBean.getDropList();
      if (dropList != null) {
        myDragOwner.moveChangesTo(dropList, dragBean.getChanges());
        final List<VirtualFile> unversionedFiles = dragBean.getUnversionedFiles();
        if (unversionedFiles != null) {
          myDragOwner.addUnversionedFiles(dropList, unversionedFiles);
        }
      }
    }

    public void cleanUpOnLeave() {
    }

    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }
  }

  private static class NodeToTextConvertor implements Convertor<TreePath, String> {
    public String convert(final TreePath path) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      final Object object = node.getUserObject();
      if (object instanceof ChangeList) {
        final ChangeList list = ((ChangeList)object);
        return list.getName();
      }
      else if (object instanceof Change) {
        final Change change = (Change)object;
        final FilePath filePath = ChangesUtil.getFilePath(change);
        return filePath.getName();
      }
      else if (object instanceof VirtualFile) {
        final VirtualFile file = (VirtualFile)object;
        return file.getName();
      }
      else if (object instanceof FilePath) {
        final FilePath filePath = (FilePath)object;
        return filePath.getName();
      }
      else if (object instanceof Module) {
        final Module module = (Module)object;
        return module.getName();
      }

      return node.toString();
    }
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return action == DnDAction.MOVE && (getSelectedChanges().length > 0 || getSelectedUnversionedFiles().size() > 0);
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    return new DnDDragStartBean(new ChangeListDragBean(this, getSelectedChanges(), getSelectedUnversionedFiles()));
  }

  @Nullable
  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final Image image = DragImageFactory.createImage(this);
    return new Pair<Image, Point>(image, new Point(-image.getWidth(null), -image.getHeight(null)));
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(final int gestureModifiers) {
  }

  @NotNull
  public JComponent getComponent() {
    return this;
  }

  public void processMouseEvent(final MouseEvent e) {
    if (MouseEvent.MOUSE_RELEASED == e.getID() && !isSelectionEmpty() && !e.isShiftDown()) {
      if (isOverSelection(e.getPoint())) {
        clearSelection();
        final TreePath path = getPathForLocation(e.getPoint().x, e.getPoint().y);
        if (path != null) {
          setSelectionPath(path);
          e.consume();
        }
      }
    }


    super.processMouseEvent(e);
  }

  public boolean isOverSelection(final Point point) {
    return TreeUtils.isOverSelection(this, point);
  }
}
