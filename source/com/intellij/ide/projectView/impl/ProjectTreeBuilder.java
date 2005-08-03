package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.properties.PropertiesFilesManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.psi.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

public class ProjectTreeBuilder extends BaseProjectTreeBuilder {
  private final ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private final ModuleRootListener myModuleRootListener;
  private final MyFileStatusListener myFileStatusListener;

  private final CopyPasteUtil.DefaultCopyPasteListener myCopyPasteListener;
  private final ProjectTreeBuilder.PropertiesFileListener myPropertiesFileListener;

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
    myCopyPasteListener = new CopyPasteUtil.DefaultCopyPasteListener(myUpdater);
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);

    myPropertiesFileListener = new PropertiesFileListener();
    PropertiesFilesManager.getInstance().addPropertiesFileListener(myPropertiesFileListener);
    initRootNode();
  }

  public final void dispose() {
    super.dispose();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
    PropertiesFilesManager.getInstance().removePropertiesFileListener(myPropertiesFileListener);
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

      final boolean fileAdded = myUpdater.addSubtreeToUpdateByElement(element);
      if (!fileAdded) {
        if (element instanceof PsiFile) {
          myUpdater.addSubtreeToUpdateByElement(((PsiFile)element).getContainingDirectory());
        } else {
          myUpdater.addSubtreeToUpdate(myRootNode);
        }
      }
    }
  }

  private class PropertiesFileListener implements PropertiesFilesManager.PropertiesFileListener {
    public void fileAdded(VirtualFile propertiesFile) {
      fileChanged(propertiesFile, null);
    }

    public void fileRemoved(VirtualFile propertiesFile) {
      fileChanged(propertiesFile, null);
    }

    public void fileChanged(VirtualFile propertiesFile, final VirtualFilePropertyEvent event) {
      if (!myProject.isDisposed()) {
        VirtualFile parent = propertiesFile.getParent();
        if (parent != null && parent.isValid()) {
          PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(parent);
          myUpdater.addSubtreeToUpdateByElement(dir);
        }
      }
    }
  }
}