package com.intellij.ide.structureView.impl;

import com.intellij.ide.impl.StructureViewWrapper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

final class StructureTreeBuilder extends AbstractTreeBuilder {
  private final Project myProject;
  private final PsiFile myFile;

//  private boolean myGroupOverridingMethods;
//  private boolean myGroupImplementingMethods;

  private final MyCopyPasteListener myCopyPasteListener;
  private final PsiTreeChangeListener myPsiTreeChangeListener;
  private final StructureViewWrapper myWrapper;

  public StructureTreeBuilder(Project project,
                            JTree tree,
                            DefaultTreeModel treeModel,
                            PsiFile file,
                            AbstractTreeStructure treeStructure,
                            StructureViewWrapper wrapper) {
    super(
      tree,
      treeModel,
      treeStructure, null
    );

    myProject = project;
    myFile = file;
    myWrapper = wrapper;

    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);

    myCopyPasteListener = new MyCopyPasteListener();
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);

    initRootNode();
  }

  public void dispose() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
    super.dispose();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return true;
  }

  protected boolean isSmartExpand() {
    return false;
  }

  protected final AbstractTreeUpdater createUpdater(){
    return new AbstractTreeUpdater(this){
      protected void updateSubtree(DefaultMutableTreeNode node){
        if (!myFile.isValid()) {
          myWrapper.rebuild();
          return;
        }
        Module module = ModuleUtil.findModuleForPsiElement(myFile);
        if (module != null && module.isDisposed()) return;

        super.updateSubtree(node);
      }
    };
  }
  
  
  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childRemoved(PsiTreeChangeEvent event) {
      PsiElement child = event.getOldChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged(event.getFile(), event.getParent());
    }

    public void childAdded(PsiTreeChangeEvent event) {
      PsiElement child = event.getNewChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged(event.getFile(), event.getParent());
    }

    public void childReplaced(PsiTreeChangeEvent event) {
      PsiElement oldChild = event.getOldChild();
      PsiElement newChild = event.getNewChild();
      if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
      if (oldChild instanceof PsiCodeBlock && newChild instanceof PsiCodeBlock) return; //optimization
      childrenChanged(event.getFile(), event.getParent());
    }

    public void childMoved(PsiTreeChangeEvent event) {
      childrenChanged(event.getFile(), event.getOldParent());
      childrenChanged(event.getFile(), event.getNewParent());
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      childrenChanged(event.getFile(), event.getParent());
    }

    private void childrenChanged(PsiFile file, PsiElement parent) {
      if (!myFile.isValid()) {
        myUpdater.cancelAllRequests();
        return;
      }

      if (!myFile.equals(file)){
        myUpdater.addSubtreeToUpdate(myRootNode);
        return;
      }

      // See SCR 8388
      myUpdater.addSubtreeToUpdate(myRootNode);
      return;

    }

    public void propertyChanged(PsiTreeChangeEvent event) {
      String propertyName = event.getPropertyName();
      PsiElement element = event.getElement();
      if (propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
        if (element.equals(myFile)){
          myUpdater.addSubtreeToUpdate(myRootNode);
        }
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME)){
        if (element.equals(myFile)){
          myUpdater.addSubtreeToUpdate(myRootNode);
        }
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_TYPES)){
        myUpdater.addSubtreeToUpdate(myRootNode);
      }
      else{
        myUpdater.addSubtreeToUpdate(myRootNode);
      }
    }
  }

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged() {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }
}