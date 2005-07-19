/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;
import java.util.Set;

public class FavoritesViewTreeBuilder extends BaseProjectTreeBuilder {
  private ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private ModuleRootListener myModuleRootListener;
  private FileStatusListener myFileStatusListener;
  private CopyPasteUtil.DefaultCopyPasteListener myCopyPasteListener;

  public FavoritesViewTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel, ProjectAbstractTreeStructureBase treeStructure) {
    super(project, tree, treeModel, treeStructure, null);
    setNodeDescriptorComparator(new Comparator<NodeDescriptor>(){
      private int getWeight(NodeDescriptor descriptor) {
        FavoritesTreeNodeDescriptor favoritesTreeNodeDescriptor = (FavoritesTreeNodeDescriptor)descriptor;
        Object value = favoritesTreeNodeDescriptor.getElement().getValue();
        if (value instanceof SmartPsiElementPointer){
          value = ((SmartPsiElementPointer)value).getElement();
        }
        if (value instanceof ModuleGroup){
          return 0;
        }
        if (value instanceof Module){
          return 1;
        }
        if (value instanceof PsiDirectory || value instanceof PackageElement){
          return 2;
        }
        if (value instanceof PsiFile){
          return 4;
        }
        if (value instanceof PsiClass){
          return 5;
        }
        if (value instanceof PsiField){
          return 6;
        }
        if (value instanceof PsiMethod){
          return 7;
        }
        if (value instanceof PsiElement){
          return 8;
        }
        if (value instanceof Form){
          return 9;
        }
        if (value instanceof LibraryGroupElement){
          return 10;
        }
        if (value instanceof NamedLibraryElement){
          return 11;
        }
        return 12;
      }

      public int compare(NodeDescriptor nd1, NodeDescriptor nd2) {
        if (nd1 instanceof FavoritesTreeNodeDescriptor && nd2 instanceof FavoritesTreeNodeDescriptor){
          FavoritesTreeNodeDescriptor fd1 = (FavoritesTreeNodeDescriptor)nd1;
          FavoritesTreeNodeDescriptor fd2 = (FavoritesTreeNodeDescriptor)nd2;
          int weight1 = getWeight(fd1);
          int weight2 = getWeight(fd2);
          if (weight1 != weight2) {
            return weight1 - weight2;
          }
          String s1 = fd1.toString();
          String s2 = fd2.toString();
          if (s1 == null) return s2 == null ? 0 : -1;
          if (s2 == null) return +1;
          if (!s1.equals(s2)) {
            return s1.compareToIgnoreCase(s2);
          }
          else {
            s1 = fd1.getLocation();
            s2 = fd2.getLocation();
            if (s1 == null) return s2 == null ? 0 : -1;
            if (s2 == null) return +1;
            return s1.compareToIgnoreCase(s2);
          }
        }
        return 0;
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

      protected void childrenChanged(PsiElement parent) {
        if (findNodeByElement(parent) == null){
          getUpdater().addSubtreeToUpdate(getRootNode());
        } else {
          super.childrenChanged(parent);
        }
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
    myCopyPasteListener = new CopyPasteUtil.DefaultCopyPasteListener(myUpdater);
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

  public void select(Object element, VirtualFile file, boolean requestFocus) {
    final DefaultMutableTreeNode node = findSmartFirstLevelNodeByElement(element);
    if (node != null){
      TreeUtil.selectInTree(node, requestFocus, getTree());
      return;
    }
    super.select(element, file, requestFocus);
  }

  @Nullable
  private static DefaultMutableTreeNode findFirstLevelNodeWithObject(final DefaultMutableTreeNode aRoot, final Object aObject) {
    for (int i = 0; i < aRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)aRoot.getChildAt(i);
      Object userObject = child.getUserObject();
      if (userObject instanceof FavoritesTreeNodeDescriptor) {
        if (Comparing.equal(((FavoritesTreeNodeDescriptor)userObject).getElement(), aObject)) {
          return child;
        }
      }
    }
    return null;
  }

  protected Object findNodeByElement(Object element) {
    final Object node = findSmartFirstLevelNodeByElement(element);
    if (node != null) return node;
    return super.findNodeByElement(element);
  }

  @Nullable
  private DefaultMutableTreeNode findSmartFirstLevelNodeByElement(final Object element) {
    final Set<AbstractTreeNode> favorites = ((FavoritesTreeStructure)getTreeStructure()).getFavorites();
    for (AbstractTreeNode favorite : favorites) {
      Object currentValue = favorite.getValue();
      if (currentValue instanceof SmartPsiElementPointer){
        currentValue = ((SmartPsiElementPointer)favorite.getValue()).getElement();
      } else if (currentValue instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)currentValue).getClasses();
        if (classes.length > 0) {
          currentValue = classes[0];
        }
      }
      if (Comparing.equal(element, currentValue)){
        final DefaultMutableTreeNode nodeWithObject = findFirstLevelNodeWithObject((DefaultMutableTreeNode)getTree().getModel().getRoot(), favorite);
        if (nodeWithObject != null){
          return nodeWithObject;
        }
      }
    }
    return null;
  }

  public final void dispose() {
    super.dispose();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
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

}

