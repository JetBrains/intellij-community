package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
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
import java.util.Comparator;

public class ProjectTreeBuilder extends BaseProjectTreeBuilder {
  private final ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private final ModuleRootListener myModuleRootListener;
  private final MyFileStatusListener myFileStatusListener;

  private final MyCopyPasteListener myCopyPasteListener;

  public ProjectTreeBuilder(final Project project, JTree tree, DefaultTreeModel treeModel, Comparator<NodeDescriptor> comparator, ProjectAbstractTreeStructureBase treeStructure) {
    super(project, tree, treeModel, treeStructure, comparator);
    myPsiTreeChangeListener = new ProjectViewPsiTreeChangeListener(){
      protected DefaultMutableTreeNode getRootNode(){
        return myRootNode;
      }

      protected AbstractTreeUpdater getUpdater(){
        return myUpdater;
      }

      protected boolean isFlattenPackages(){
        return ((AbstractProjectTreeStructure)getTreeStructure()).isFlattenPackages();
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

  public final void dispose() {
    super.dispose();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
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