/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class FavoritesViewTreeBuilder extends BaseProjectTreeBuilder {
  private ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private ModuleRootListener myModuleRootListener;
  private Project myProject;
  private FileStatusListener myFileStatusListener;
  private MyCopyPasteListener myCopyPasteListener;

  public FavoritesViewTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel, ProjectAbstractTreeStructureBase treeStructure) {
    super(project, tree, treeModel, treeStructure, IndexComparator.INSTANCE);
    myProject = project;
    myPsiTreeChangeListener = new ProjectViewPsiTreeChangeListener() {
      protected DefaultMutableTreeNode getRootNode() {
        return myRootNode;
      }

      protected AbstractTreeUpdater getUpdater() {
        return myUpdater;
      }

      protected boolean isFlattenPackages() {
        return ((FavoritesTreeStructure)myTreeStructure).getFavoritesConfiguration().IS_FLATTEN_PACKAGES;
      }
    };
    myModuleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    ProjectRootManager.getInstance(myProject).addModuleRootListener(myModuleRootListener);
    myFileStatusListener = new MyFileStatusListener();
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    myCopyPasteListener = new MyCopyPasteListener();
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);
    initRootNode();
  }


  public void updateFromRoot() {
    myUpdater.cancelAllRequests();
    super.updateFromRoot();
  }

  public void updateTree() {
    myUpdater.addSubtreeToUpdate(myRootNode);
  }

  public void updateTree(Runnable runAferUpdate) {
    myUpdater.runAfterUpdate(runAferUpdate);
    updateTree();
  }

  public final void dispose() {
    super.dispose();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object[] childElements = ((FavoritesTreeStructure)myTreeStructure).getChildElements(nodeDescriptor);
    return childElements != null ? childElements.length > 0 : false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null;
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public void fileStatusChanged(VirtualFile vFile) {
      PsiElement element;
      PsiManager psiManager = PsiManager.getInstance(myProject);
      if (vFile.isDirectory()) {
        element = psiManager.findDirectory(vFile);
      }
      else {
        element = psiManager.findFile(vFile);
      }

      if (!myUpdater.addSubtreeToUpdateByElement(element) && element instanceof PsiJavaFile) {
        myUpdater.addSubtreeToUpdateByElement(((PsiJavaFile)element).getContainingDirectory());
      }
    }
  }

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged() {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }
}

