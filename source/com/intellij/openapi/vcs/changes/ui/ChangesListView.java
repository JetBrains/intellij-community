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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesListView extends Tree implements DataProvider, DeleteProvider {
  private ChangesListView.DragSource myDragSource;
  private ChangesListView.DropTarget myDropTarget;
  private DnDManager myDndManager;
  private ChangeListOwner myDragOwner;
  private final Project myProject;
  private FileStatusListener myFileStatusManager;
  private TreeState myTreeState;
  private boolean myShowFlatten = false;
  @NonNls private static final String ROOT_NODE_VALUE = "root";

  @NonNls private static final String UNVERSIONED_FILES_KEY = "ChangeListView.UnversionedFiles";

  private FileStatus getChangeStatus(Change change) {
    final VirtualFile vFile = ChangesUtil.getFilePath(change).getVirtualFile();
    if (vFile == null) return FileStatus.DELETED;
    return FileStatusManager.getInstance(myProject).getStatus(vFile);
  }

  public ChangesListView(final Project project) {
    myProject = project;

    getModel().setRoot(new Node(ROOT_NODE_VALUE));

    setShowsRootHandles(true);
    setRootVisible(false);
    setCellRenderer(new NodeRenderer());
    new TreeSpeedSearch(this, new NodeToTextConvertor());
    SmartExpander.installOn(this);

    myFileStatusManager = new FileStatusListener() {
      public void fileStatusesChanged() {
        reload();
      }

      public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
        reload();
      }
    };

    FileStatusManager.getInstance(project).addFileStatusListener(myFileStatusManager);
  }

  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  public static FilePath safeCastToFilePath(Object o) {
    if (o instanceof FilePath) return (FilePath)o;
    return null;
  }

  public static String getRelativePath(FilePath parent, FilePath child) {
    if (parent == null) return child.getPath();
    return child.getPath().substring(parent.getPath().length() + 1).replace('/', File.separatorChar);
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
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusManager);
    if (myDragSource != null) {
      myDndManager.unregisterSource(myDragSource, this);
      myDndManager.unregisterTarget(myDropTarget, this);

      myDragSource = null;
      myDropTarget = null;
      myDndManager = null;
      myDragOwner = null;
    }
  }

  private void reload() {
    storeState();
    getModel().reload();
    restoreState();
  }

  private void storeState() {
    myTreeState = TreeState.createOn(this, (Node)getModel().getRoot());
  }

  private void restoreState() {
    myTreeState.applyTo(this, (Node)getModel().getRoot());
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setShowFlatten(final boolean showFlatten) {
    myShowFlatten = showFlatten;
  }

  public void updateModel(List<ChangeList> changeLists, List<VirtualFile> unversionedFiles, final List<File> locallyDeletedFiles) {
    storeState();

    final DefaultTreeModel model = buildModel(changeLists, unversionedFiles, locallyDeletedFiles);
    setModel(model);

    sortNodes();
    expandPath(new TreePath(((Node)model.getRoot()).getPath()));

    restoreState();
  }

  private void sortNodes() {
    DefaultTreeModel model = getModel();
    TreeUtil.sort(getModel(), new Comparator() {
      public int compare(final Object n1, final Object n2) {
        Object o1 = ((Node)n1).getUserObject();
        Object o2 = ((Node)n2).getUserObject();

        final int classdiff = getNodeClassWeight(o1) - getNodeClassWeight(o2);
        if (classdiff != 0) return classdiff;

        if (o1 instanceof Change && o2 instanceof Change) {
          return ChangesUtil.getFilePath((Change)o1).getName().compareToIgnoreCase(ChangesUtil.getFilePath((Change)o2).getName());
        }

        if (o1 instanceof ChangeList && o2 instanceof ChangeList) {
          return ((ChangeList)o1).getDescription().compareToIgnoreCase(((ChangeList)o2).getDescription());
        }

        if (o1 instanceof VirtualFile && o2 instanceof VirtualFile) {
          return ((VirtualFile)o1).getName().compareToIgnoreCase(((VirtualFile)o2).getName());
        }

        if (o1 instanceof FilePath && o2 instanceof FilePath) {
          return ((FilePath)o1).getPath().compareToIgnoreCase(((FilePath)o2).getPath());
        }

        return 0;
      }

      private int getNodeClassWeight(Object userObject) {
        if (userObject instanceof ChangeList) {
          if (((ChangeList)userObject).isDefault()) return 1;
          return 2;
        }

        if (userObject instanceof Module) return 3;

        if (userObject instanceof FilePath) {
          if (((FilePath)userObject).isDirectory()) return 4;
          return 5;
        }

        if (userObject instanceof Change) return 6;
        if (userObject instanceof VirtualFile) return 7;
        return 8;
      }

    });

    model.nodeStructureChanged((TreeNode)model.getRoot());
  }

  private DefaultTreeModel buildModel(final List<ChangeList> changeLists,
                                      final List<VirtualFile> unversionedFiles,
                                      final List<File> locallyDeletedFiles) {
    Node root = new Node(ROOT_NODE_VALUE);
    final DefaultTreeModel model = new DefaultTreeModel(root);

    for (ChangeList list : changeLists) {
      Node listNode = new Node(list);
      model.insertNodeInto(listNode, root, 0);
      final HashMap<FilePath, Node> foldersCache = new HashMap<FilePath, Node>();
      final HashMap<Module, Node> moduleCache = new HashMap<Module, Node>();
      for (Change change : list.getChanges()) {
        final Node node = new Node(change);
        ChangesUtil.getFilePath(change).refresh();
        model.insertNodeInto(node, getParentNodeFor(node, foldersCache, moduleCache, listNode), 0);
      }
    }

    if (!unversionedFiles.isEmpty()) {
      Node unversionedNode = new Node(VcsBundle.message("changes.nodetitle.unversioned.files"));
      model.insertNodeInto(unversionedNode, root, root.getChildCount());
      final HashMap<FilePath, Node> foldersCache = new HashMap<FilePath, Node>();
      final HashMap<Module, Node> moduleCache = new HashMap<Module, Node>();
      for (VirtualFile file : unversionedFiles) {
        final Node node = new Node(file);
        model.insertNodeInto(node, getParentNodeFor(node, foldersCache, moduleCache, unversionedNode), 0);
      }
    }

    if (!locallyDeletedFiles.isEmpty()) {
      Node locallyDeletedNode = new Node("Locally Deleted");
      model.insertNodeInto(locallyDeletedNode, root, root.getChildCount());
      final VcsContextFactory factory = PeerFactory.getInstance().getVcsContextFactory();
      final HashMap<FilePath, Node> foldersCache = new HashMap<FilePath, Node>();
      final HashMap<Module, Node> moduleCache = new HashMap<Module, Node>();
      for (File file : locallyDeletedFiles) {
        final Node node = new Node(factory.createFilePathOn(file));
        model.insertNodeInto(node, getParentNodeFor(node, foldersCache, moduleCache, locallyDeletedNode), 0);
      }
    }

    collapseDirectories(model, root);

    return model;
  }

  private static void collapseDirectories(DefaultTreeModel model, Node node) {
    if (node.getUserObject() instanceof FilePath && node.getChildCount() == 1) {
      final Node child = (Node)node.getChildAt(0);
      if (child.getUserObject() instanceof FilePath && ((FilePath)child.getUserObject()).isDirectory()) {
        Node parent = (Node)node.getParent();
        final int idx = parent.getIndex(node);
        model.removeNodeFromParent(node);
        model.removeNodeFromParent(child);
        model.insertNodeInto(child, parent, idx);
        collapseDirectories(model, parent);
      }
    }
    else {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        Node child = (Node)children.nextElement();
        collapseDirectories(model, child);
      }
    }
  }

  private static FilePath getPathForObject(Object o) {
    if (o instanceof Change) {
      return ChangesUtil.getFilePath((Change)o);
    }
    else if (o instanceof VirtualFile) {
      return PeerFactory.getInstance().getVcsContextFactory().createFilePathOn((VirtualFile)o);
    }
    else if (o instanceof FilePath) {
      return (FilePath)o;
    }

    return null;
  }

  private Node getParentNodeFor(Node node, Map<FilePath, Node> folderNodesCache, Map<Module, Node> moduleNodesCache, Node rootNode) {
    if (isShowFlatten()) {
      return rootNode;
    }

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    final FilePath path = getPathForObject(node.getUserObject());

    final VirtualFile rootFolder = VcsDirtyScope.getRootFor(index, path);
    if (rootFolder == null) {
      return rootNode;
    }

    if (path.getVirtualFile() == rootFolder) {
      Module module = index.getModuleForFile(rootFolder);
      return getNodeForModule(module, moduleNodesCache, rootNode);
    }

    FilePath parentPath = getParentPath(path);

    Node parentNode = folderNodesCache.get(parentPath);
    if (parentNode == null) {
      parentNode = new Node(parentPath);
      Node grandPa = getParentNodeFor(parentNode, folderNodesCache, moduleNodesCache, rootNode);
      getModel().insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
      folderNodesCache.put(parentPath, parentNode);
    }

    return parentNode;
  }

  private static FilePath getParentPath(final FilePath path) {
    return PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(path.getIOFile().getParentFile());
  }

  private Node getNodeForModule(final Module module, final Map<Module, Node> moduleNodesCache, Node root) {
    Node node = moduleNodesCache.get(module);
    if (node == null) {
      node = new Node(module);
      getModel().insertNodeInto(node, root, root.getChildCount());
      moduleNodesCache.put(module, node);
    }
    return node;
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
      final VirtualFile[] files = getSelectedFiles();
      Navigatable[] navigatables = new Navigatable[files.length];
      for (int i = 0; i < files.length; i++) {
        navigatables[i] = new OpenFileDescriptor(myProject, files[i], 0);
      }
      return navigatables;
    }
    else if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return this;
    }
    else if (UNVERSIONED_FILES_KEY.equals(dataId)) {
      return getSelectedUnversionedFiles();
    }

    return null;
  }

  private List<VirtualFile> getSelectedUnversionedFiles() {
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        Node node = (Node)path.getLastPathComponent();
        files.addAll(getAllFilesUnder(node));
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
      Node node = (Node)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof Change) {
        changes.add((Change)userObject);
      }
      else if (userObject instanceof FilePath || userObject instanceof Module) {
        changes.addAll(getAllChangesUnder(node));
      }
    }

    return changes.toArray(new Change[changes.size()]);
  }

  private static List<Change> getAllChangesUnder(final Node node) {
    List<Change> changes = new ArrayList<Change>();
    final Enumeration enumeration = node.breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      Node child = (Node)enumeration.nextElement();
      final Object value = child.getUserObject();
      if (value instanceof Change) {
        changes.add((Change)value);
      }
    }
    return changes;
  }

  private static List<VirtualFile> getAllFilesUnder(final Node node) {
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    final Enumeration enumeration = node.breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      Node child = (Node)enumeration.nextElement();
      final Object value = child.getUserObject();
      if (value instanceof VirtualFile) {
        final VirtualFile file = (VirtualFile)value;
        if (file.isValid()) {
          files.add(file);
        }
      }
    }

    return files;
  }

  @NotNull
  private ChangeList[] getSelectedChangeLists() {
    Set<ChangeList> lists = new HashSet<ChangeList>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new ChangeList[0];

    for (TreePath path : paths) {
      Node node = (Node)path.getLastPathComponent();
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

  public class DragSource implements DnDSource {
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      return action == DnDAction.MOVE && getSelectedChanges().length > 0;
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
      final int height = Math.max(20, Math.min(100, tree.getSelectionCount() * tree.getRowHeight()));
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
      Node dropNode = (Node)dropPath.getLastPathComponent();
      do {
        if (dropNode == null || dropNode.isRoot()) return false;
        object = dropNode.getUserObject();
        if (object instanceof ChangeList) break;
        dropNode = (Node)dropNode.getParent();
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

      aEvent.setHighlighting(new RelativeRectangle(ChangesListView.this, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);

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

  private class NodeRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      Node node = (Node)value;
      Object object = node.getUserObject();
      if (object instanceof ChangeList) {
        final ChangeList list = ((ChangeList)object);
        append(list.getDescription(),
               list.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        appendCount(node);
        if (list.isInUpdate()) {
          append(" " + VcsBundle.message("changes.nodetitle.updating"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
      else if (object instanceof Change) {
        final Change change = (Change)object;
        final FilePath filePath = ChangesUtil.getFilePath(change);
        append(filePath.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor(change), null));
        if (isShowFlatten()) {
          append(" (" + filePath.getIOFile().getParentFile().getPath() + ", " + getChangeStatus(change).getText() + ")",
                 SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        if (filePath.isDirectory()) {
          setIcon(Icons.DIRECTORY_CLOSED_ICON);
        }
        else {
          setIcon(filePath.getFileType().getIcon());
        }
      }
      else if (object instanceof VirtualFile) {
        final VirtualFile file = (VirtualFile)object;
        append(file.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.COLOR_UNKNOWN));
        if (isShowFlatten() && file.isValid()) {
          final VirtualFile parentFile = file.getParent();
          assert parentFile != null;
          append(" (" + parentFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        setIcon(file.getFileType().getIcon());
      }
      else if (object instanceof FilePath) {
        final FilePath path = (FilePath)object;
        append(getRelativePath(safeCastToFilePath(((Node)node.getParent()).getUserObject()), path),
               SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (path.isDirectory()) {
          appendCount(node);
          setIcon(expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON);
        }
        else {
          if (isShowFlatten()) {
            final FilePath parent = getParentPath(path);
            append(" (" + parent.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          setIcon(path.getFileType().getIcon());
        }
      }
      else if (object instanceof Module) {
        final Module module = (Module)object;

        append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        appendCount(node);
        setIcon(module.getModuleType().getNodeIcon(expanded));
      }
      else {
        append(object.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        appendCount(node);
      }
    }

    private void appendCount(final Node node) {
      append(" " + VcsBundle.message("changes.nodetitle.changecount", node.getCount()), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }

    private Color getColor(final Change change) {
      return getChangeStatus(change).getColor();
    }
  }

  private static class NodeToTextConvertor implements Convertor<TreePath, String> {
    public String convert(final TreePath path) {
      Node node = (Node)path.getLastPathComponent();
      final Object object = node.getUserObject();
      if (object instanceof ChangeList) {
        final ChangeList list = ((ChangeList)object);
        return list.getDescription();
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

  private static class Node extends DefaultMutableTreeNode {
    private int count = -1;

    public Node(Object userObject) {
      super(userObject);
      if (userObject instanceof Change || userObject instanceof VirtualFile ||
          userObject instanceof FilePath && !((FilePath)userObject).isDirectory()) {
        count = 1;
      }
    }

    public int getCount() {
      if (count == -1) {
        count = 0;
        final Enumeration nodes = children();
        while (nodes.hasMoreElements()) {
          Node child = (Node)nodes.nextElement();
          count += child.getCount();
        }
      }
      return count;
    }
  }
}
