package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
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
import com.intellij.psi.*;
import com.intellij.lang.properties.PropertiesFilesManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.datatransfer.Transferable;
import java.util.Comparator;

public class ProjectTreeBuilder extends BaseProjectTreeBuilder {
  private final ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private final ModuleRootListener myModuleRootListener;
  private final MyFileStatusListener myFileStatusListener;

  private final MyCopyPasteListener myCopyPasteListener;
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
    myCopyPasteListener = new MyCopyPasteListener();
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

      if (!myUpdater.addSubtreeToUpdateByElement(element) && element instanceof PsiJavaFile) {
        myUpdater.addSubtreeToUpdateByElement(((PsiJavaFile)element).getContainingDirectory());
      }
    }
  }

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged(final Transferable oldTransferable, final Transferable newTransferable) {
      updateByTransferable(oldTransferable);
      updateByTransferable(newTransferable);
    }

    private void updateByTransferable(final Transferable t) {
      final PsiElement[] psiElements = CopyPasteUtil.getElementsInTransferable(t);
      for (PsiElement psiElement : psiElements) {
        myUpdater.addSubtreeToUpdateByElement(psiElement);
      }
    }
  }

  private class PropertiesFileListener implements PropertiesFilesManager.PropertiesFileListener {
    public void fileAdded(VirtualFile propertiesFile) {
    }

    public void fileRemoved(VirtualFile propertiesFile) {
    }

    public void fileChanged(VirtualFile propertiesFile) {
      if (!myProject.isDisposed()) {
        PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(propertiesFile.getParent());
        myUpdater.addSubtreeToUpdateByElement(dir);
      }
    }
  }
}