/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.TreeBuilderUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.StringTokenizer;

public abstract class AbstractProjectViewPSIPane extends AbstractProjectViewPane {
  protected JScrollPane myComponent;

  protected AbstractProjectViewPSIPane(Project project) {
    super(project);
    myExpandedElements.registerExpandedElementProvider(new ClassUrl(null, null));
    myExpandedElements.registerExpandedElementProvider(new ModuleUrl(null, null));
    myExpandedElements.registerExpandedElementProvider(new DirectoryUrl(null, null));
  }

  protected final void initPSITree() {
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.putClientProperty("JTree.lineStyle", "Angled");
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandPath(new TreePath(myTree.getModel().getRoot()));
    myTree.setSelectionPath(new TreePath(myTree.getModel().getRoot()));

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    PopupHandler.installPopupHandler(myTree, group, ActionPlaces.PROJECT_VIEW_POPUP, ActionManager.getInstance());

    EditSourceOnDoubleClickHandler.install(myTree);

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

                                    DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
                                    Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
                                    if (navigatable != null) {
                                      navigatable.navigate(false);
                                    }
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

    myExpandedElements.restoreExpandedElements(this, myProject);
  }

  public final void expand(Object[] path) {
    myTreeBuilder.buildNodeForPath(path);
    DefaultMutableTreeNode node = myTreeBuilder.getNodeForPath(path);
    if (node == null) {
      if (path.length != 0) {
        expand(path[path.length - 1]);
      }
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
  }

  public final void expand(Object element) {
    myTreeBuilder.buildNodeForElement(element);
    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node == null) {
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
  }

  public final void dispose() {
    myTreeBuilder.dispose();
  }

  public final void updateFromRoot(boolean restoreExpandedPaths) {
    final ArrayList pathsToExpand = new ArrayList();
    final ArrayList selectionPaths = new ArrayList();
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
    myTreeBuilder.select(element, file, requestFocus, myTreeBuilder);
  }

  public final void selectModule(Module module, boolean requestFocus) {
  }

  public final TreePath[] getSelectionPaths() {
    return myTree.getSelectionPaths();
  }

  public final void installAutoScrollToSourceHandler(AutoScrollToSourceHandler autoScrollToSourceHandler) {
    autoScrollToSourceHandler.install(myTree);
  }

  public void initTree() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = createTree(treeModel);
    myComponent = new JScrollPane(myTree);
    myComponent.setBorder(BorderFactory.createEmptyBorder());
    myTreeStructure = createStructure();
    myTreeBuilder = createBuilder(treeModel);

    SelectInManager selectInManager = SelectInManager.getInstance(myProject);
    selectInManager.addTarget(createSelectInTarget());

    initPSITree();
  }

  protected abstract ProjectViewSelectInTarget createSelectInTarget();

  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, AlphaComparator.INSTANCE,
                                  (ProjectAbstractTreeStructureBase)myTreeStructure) {
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }

    };
  }

  protected abstract ProjectAbstractTreeStructureBase createStructure();

  protected abstract ProjectViewTree createTree(DefaultTreeModel treeModel);

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
                                                                            public void run() {
                                                                              initTree();
                                                                              final ProjectView projectView =
                                                                              ProjectView.getInstance(myProject);
                                                                              projectView.addProjectPane(AbstractProjectViewPSIPane.this);
                                                                            }
                                                                          });
  }

  public void projectClosed() {
    dispose();
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  protected abstract AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder);

  public JTree getTree() {
    return myTree;
  }


  protected static final class MySpeedSearch extends TreeSpeedSearch {
    MySpeedSearch(Tree tree) {
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

  public JComponent getComponent() {
    return myComponent;
  }

  public void readExternal(Element viewElement) throws InvalidDataException {
    myExpandedElements.readExternal(viewElement);
  }

  public void writeExternal(Element viewElement) {
    new ExpandedElements(getExpandedUrls()).writeExternal(viewElement);
  }


}