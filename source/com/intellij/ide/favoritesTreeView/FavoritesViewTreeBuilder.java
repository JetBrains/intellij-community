/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.datatransfer.Transferable;

public class FavoritesViewTreeBuilder extends BaseProjectTreeBuilder {
  private ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private final PsiTreeChangeListener myPsiTreeChangeAdapter;
  private ModuleRootListener myModuleRootListener;
  private FileStatusListener myFileStatusListener;
  private MyCopyPasteListener myCopyPasteListener;

  public FavoritesViewTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel, ProjectAbstractTreeStructureBase treeStructure) {
    super(project, tree, treeModel, treeStructure, null);
    setNodeDescriptorComparator(new AlphaComparator(){
      protected int getWeight(NodeDescriptor descriptor) {
        FavoritesTreeNodeDescriptor favoritesTreeNodeDescriptor = (FavoritesTreeNodeDescriptor)descriptor;
        final Object value = favoritesTreeNodeDescriptor.getElement().getValue();
        if (value instanceof ModuleGroup){
          return 0;
        }
        if (value instanceof Module){
          return 1;
        }
        if (value instanceof PsiDirectory || value instanceof PackageElement){
          return 2;
        }
        if (value instanceof PsiClass){
          return 4;
        }
        if (value instanceof PsiFile){
          return 5;
        }
        if (value instanceof PsiElement){
          return 6;
        }
        if (value instanceof Form){
          return 7;
        }
        if (value instanceof LibraryGroupElement){
          return 8;
        }
        if (value instanceof NamedLibraryElement){
          return 9;
        }
        return 10;
      }
    });
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
    myPsiTreeChangeAdapter = new MyPsiTreeChangeListener();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter);
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
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeAdapter);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object[] childElements = myTreeStructure.getChildElements(nodeDescriptor);
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

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged(final Transferable oldTransferable, final Transferable newTransferable) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }
}

