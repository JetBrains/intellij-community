/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.TreeBuilderUtil;
import com.intellij.idea.Main;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.StringTokenizer;

public abstract class AbstractProjectViewPSIPane extends AbstractProjectViewPane {
  protected JScrollPane myComponent;

  protected AbstractProjectViewPSIPane(Project project) {
    super(project);
  }

  public JComponent createComponent() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = createTree(treeModel);
    if (!Main.isHeadless()) {
      DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(myTree, DnDConstants.ACTION_COPY_OR_MOVE, new MyDragGestureListener());
    }
    myComponent = new JScrollPane(myTree);
    myTreeStructure = createStructure();
    myTreeBuilder = createBuilder(treeModel);

    installComparator();
    initTree();
    return myComponent;
  }

  public final void dispose() {
    myComponent = null;
    super.dispose();
  }

  private void initTree() {
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandPath(new TreePath(myTree.getModel().getRoot()));
    myTree.setSelectionPath(new TreePath(myTree.getModel().getRoot()));

    EditSourceOnDoubleClickHandler.install(myTree);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        fireTreeChangeListener();
      }
    });
    myTree.getModel().addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
        fireTreeChangeListener();
      }

      public void treeNodesInserted(TreeModelEvent e) {
        fireTreeChangeListener();
      }

      public void treeNodesRemoved(TreeModelEvent e) {
        fireTreeChangeListener();
      }

      public void treeStructureChanged(TreeModelEvent e) {
        fireTreeChangeListener();
      }
    });

    new MySpeedSearch(myTree);

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {

          final DefaultMutableTreeNode selectedNode = ((ProjectViewTree)myTree).getSelectedNode();
          if (selectedNode != null && !selectedNode.isLeaf()) {
            return;
          }

          DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
          OpenSourceUtil.openSourcesFrom(dataContext, false);
        }
        else if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
          if (e.isConsumed()) return;
          CopyPasteManagerEx copyPasteManager = (CopyPasteManagerEx)CopyPasteManager.getInstance();
          boolean[] isCopied = new boolean[1];
          if (copyPasteManager.getElements(isCopied) != null && !isCopied[0]) {
            copyPasteManager.clear();
            e.consume();
          }
        }
      }
    });
    installTreePopupHandler(ActionPlaces.PROJECT_VIEW_POPUP, IdeActions.GROUP_PROJECT_VIEW_POPUP);
  }

  public final void updateFromRoot(boolean restoreExpandedPaths) {
    final ArrayList<Object> pathsToExpand = new ArrayList<Object>();
    final ArrayList<Object> selectionPaths = new ArrayList<Object>();
    if (restoreExpandedPaths) {
      TreeBuilderUtil.storePaths(myTreeBuilder, (DefaultMutableTreeNode)myTree.getModel().getRoot(), pathsToExpand, selectionPaths, true);
    }
    myTreeBuilder.updateFromRoot();
    if (restoreExpandedPaths) {
      myTree.setSelectionPaths(new TreePath[0]);
      TreeBuilderUtil.restorePaths(myTreeBuilder, pathsToExpand, selectionPaths, true);
    }
  }

  public void select(Object element, VirtualFile file, boolean requestFocus) {
    if (file != null) {
      ((BaseProjectTreeBuilder)myTreeBuilder).select(element, file, requestFocus);
    }
  }

  @NotNull
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure) {
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }
    };
  }

  protected abstract ProjectAbstractTreeStructureBase createStructure();

  protected abstract ProjectViewTree createTree(DefaultTreeModel treeModel);

  public void projectOpened() {
    final Runnable runnable = new Runnable() {
      public void run() {
        final ProjectView projectView = ProjectView.getInstance(myProject);
        projectView.addProjectPane(AbstractProjectViewPSIPane.this);
      }
    };
    StartupManager.getInstance(myProject).registerPostStartupActivity(runnable);
  }

  public void projectClosed() {
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  protected abstract AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder);

  public JTree getTree() {
    return myTree;
  }


  protected static final class MySpeedSearch extends TreeSpeedSearch {
    MySpeedSearch(JTree tree) {
      super(tree);
    }

    protected boolean isMatchingElement(Object element, String pattern) {
      Object userObject = ((DefaultMutableTreeNode)((TreePath)element).getLastPathComponent()).getUserObject();
      if (userObject instanceof PsiDirectoryNode) {
        String str = getElementText(element);
        if (str == null) return false;
        str = str.toLowerCase();
        if (pattern.indexOf('.') >= 0) {
          return compare(str, pattern);
        }
        StringTokenizer tokenizer = new StringTokenizer(str, ".");
        while (tokenizer.hasMoreTokens()) {
          String token = tokenizer.nextToken();
          if (compare(token, pattern)) {
            return true;
          }
        }
        return false;
      }
      else {
        return super.isMatchingElement(element, pattern);
      }
    }
  }

  // big fat todo - have to figure out the way to drag something into favorites
  //------------- DnD for Favorites View -------------------
  public static final DataFlavor[] FLAVORS;
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.projectView.ProjectViewImpl");

  static {
    DataFlavor[] flavors;
    try {
      final Class aClass = MyTransferable.class;
      //noinspection HardCodedStringLiteral
      flavors = new DataFlavor[]{new DataFlavor(
                      DataFlavor.javaJVMLocalObjectMimeType + ";class=" + aClass.getName(),
                      FavoritesTreeViewPanel.ABSTRACT_TREE_NODE_TRANSFERABLE,
                      aClass.getClassLoader()
                    )};
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);  // should not happen
      flavors = new DataFlavor[0];
    }
    FLAVORS = flavors;
  }

  private static class MyTransferable implements Transferable {
    private final Object myTransferable;

    public MyTransferable(Object transferable) {
      myTransferable = transferable;
    }

    public DataFlavor[] getTransferDataFlavors() {
      return FLAVORS;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      DataFlavor[] flavors = getTransferDataFlavors();
      return ArrayUtil.find(flavors, flavor) != -1;
    }

    public Object getTransferData(DataFlavor flavor) {
      return myTransferable;
    }
  }

  private static class MyDragGestureListener implements DragGestureListener {
    public void dragGestureRecognized(DragGestureEvent dge) {
      if ((dge.getDragAction() & DnDConstants.ACTION_MOVE) == 0) return;
      DataContext dataContext = DataManager.getInstance().getDataContext();
      ProjectView projectView = (ProjectView)dataContext.getData(ProjectViewImpl.PROJECT_VIEW_DATA_CONSTANT);
      if (projectView == null) return;
      Object draggableObject = projectView.getCurrentProjectViewPane().getSelectedElement();

      if (draggableObject != null) {
        try {
          //FavoritesManager.getInstance(myProject).getCurrentTreeViewPanel().setDraggableObject(draggableObject.getClass(), draggableObject.getValue());
          final MyDragSourceListener dragSourceListener = new MyDragSourceListener();
          dge.startDrag(DragSource.DefaultMoveNoDrop, new MyTransferable(draggableObject), dragSourceListener);
        }
        catch (InvalidDnDOperationException idoe) {
          // ignore
        }
      }
    }

  }

  private static class MyDragSourceListener implements DragSourceListener {
    public void dragEnter(DragSourceDragEvent dsde) {
      dsde.getDragSourceContext().setCursor(null);
    }

    public void dragOver(DragSourceDragEvent dsde) {
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
      dsde.getDragSourceContext().setCursor(null);
    }

    public void dragDropEnd(DragSourceDropEvent dsde) { }

    public void dragExit(DragSourceEvent dse) { }
  }
}