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
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Comparator;

public final class HierarchyTreeBuilder extends AbstractTreeBuilder {
  private final Project myProject;

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

    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    myFileStatusListener = new MyFileStatusListener();

    initRootNode();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);

    Disposer.register(myProject, this);
  }

  public final Object storeExpandedAndSelectedInfo() {
    final ArrayList<Object> pathsToExpand = new ArrayList<Object>();
    final ArrayList<Object> selectionPaths = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, true);
    return new Pair<ArrayList<Object>, ArrayList<Object>>(pathsToExpand, selectionPaths);
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
      PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
      FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    }
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
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

    public final void fileStatusChanged(@NotNull final VirtualFile virtualFile) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }
}
