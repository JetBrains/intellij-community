package com.intellij.ide.hierarchy;

import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.TreeBuilderUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;

public final class HierarchyTreeBuilder extends AbstractTreeBuilder {
  private final Project myProject;

  private final MyTreeSelectionListener mySelectionListener;
  private final MyTreeExpansionListener myTreeExpansionListener;
  private final PsiTreeChangeListener myPsiTreeChangeListener;
  private final MyFileStatusListener myFileStatusListener;

  public HierarchyTreeBuilder(final Project project,
                              final JTree tree,
                              final DefaultTreeModel treeModel,
                              final HierarchyTreeStructure treeStructure,
                              final Comparator<NodeDescriptor> comparator
                              ) {
    super(tree, treeModel, treeStructure, comparator);
    myProject = project;

    mySelectionListener = new MyTreeSelectionListener();
    myTreeExpansionListener = new MyTreeExpansionListener();
    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    myFileStatusListener = new MyFileStatusListener();

    initRootNode();
    tree.addTreeSelectionListener(mySelectionListener);
    tree.addTreeExpansionListener(myTreeExpansionListener);
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);

    Disposer.register(myProject, this);
  }

  public final Object storeExpandedAndSelectedInfo() {
    final ArrayList pathsToExpand = new ArrayList();
    final ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, true);
    return new Pair(pathsToExpand, selectionPaths);
  }

  public final void restoreExpandedAndSelectedInfo(final Object info) {
    final Pair pair = (Pair)info;
    TreeBuilderUtil.restorePaths(this, (ArrayList)pair.first, (ArrayList)pair.second, true);
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor nodeDescriptor) {
    return myTreeStructure instanceof CallerMethodsTreeStructure;
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor instanceof HierarchyNodeDescriptor) {
      return false;
    }
    return true;
  }

  protected final boolean isSmartExpand() {
    return false;
  }

  protected final boolean isDisposeOnCollapsing(final NodeDescriptor nodeDescriptor) {
    return false; // prevents problems with building descriptors for invalidated elements
  }

  public final void dispose() {
    if (!isDisposed()) { // because can be called both externally and via my ProjectManagerListener, don't know what will happen earlier
      super.dispose();
      myTree.removeTreeSelectionListener(mySelectionListener);
      myTree.removeTreeExpansionListener(myTreeExpansionListener);
      PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
      FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    }
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }

  private final class MyTreeSelectionListener implements TreeSelectionListener {
    public final void valueChanged(final TreeSelectionEvent e) {
      updateSelected();
    }
  };

  private final class MyTreeExpansionListener implements TreeExpansionListener {
    public final void treeCollapsed(final TreeExpansionEvent event) {
      updateSelected();
    }

    public final void treeExpanded(final TreeExpansionEvent event) {
      updateSelected();
    }
  };

  private void updateSelected() {
    final TreePath path = myTree.getSelectionPath();
    if (path == null) return;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    final Object object = node.getUserObject();
    if (!(object instanceof HierarchyNodeDescriptor)) return;
  }

  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public final void childAdded(final PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public final void childRemoved(final PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public final void childReplaced(final PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public final void childMoved(final PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public final void childrenChanged(final PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public final void propertyChanged(final PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public final void fileStatusesChanged() {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public final void fileStatusChanged(final VirtualFile virtualFile) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }
}
