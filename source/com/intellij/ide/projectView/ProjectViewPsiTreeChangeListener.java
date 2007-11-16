package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import javax.swing.tree.DefaultMutableTreeNode;

public abstract class ProjectViewPsiTreeChangeListener extends PsiTreeChangeAdapter {
  private final FileTypeManager myFileTypeManager;

  protected ProjectViewPsiTreeChangeListener() {
    myFileTypeManager = FileTypeManager.getInstance();
  }

  protected abstract AbstractTreeUpdater getUpdater();

  protected abstract boolean isFlattenPackages();

  protected abstract DefaultMutableTreeNode getRootNode();

  public final void childRemoved(PsiTreeChangeEvent event) {
    PsiElement child = event.getOldChild();
    if (child instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent());
  }

  public final void childAdded(PsiTreeChangeEvent event) {
    PsiElement child = event.getNewChild();
    if (child instanceof PsiWhiteSpace) return; //optimization
    childrenChanged(event.getParent());
  }

  public final void childReplaced(PsiTreeChangeEvent event) {
    PsiElement oldChild = event.getOldChild();
    PsiElement newChild = event.getNewChild();
    if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
    if (oldChild instanceof PsiCodeBlock && newChild instanceof PsiCodeBlock) return; //optimization
    childrenChanged(event.getParent());
  }

  public final void childMoved(PsiTreeChangeEvent event) {
    childrenChanged(event.getOldParent());
    childrenChanged(event.getNewParent());
  }

  public final void childrenChanged(PsiTreeChangeEvent event) {
    childrenChanged(event.getParent());
  }

  protected void childrenChanged(PsiElement parent) {
    if (parent instanceof PsiDirectory && isFlattenPackages()){
      getUpdater().addSubtreeToUpdate(getRootNode());
      return;
    }

    while(true){
      if (parent == null) break;
      if (parent instanceof PsiCodeBlock) break;
      if (parent instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)parent).getVirtualFile();
        if (virtualFile != null && myFileTypeManager.getFileTypeByFile(virtualFile) != FileTypes.PLAIN_TEXT) {
          // adding a class within a file causes a new node to appear in project view => entire dir should be updated
          parent = ((PsiFile)parent).getContainingDirectory();
          if (parent == null) break;
        }
      }

      if (getUpdater().addSubtreeToUpdateByElement(parent)){
        break;
      }

      if (parent instanceof PsiMember ||
          parent instanceof PsiFile ||
          parent instanceof PsiDirectory) break;
      parent = parent.getParent();
    }
  }

  public final void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    PsiElement element = event.getElement();
    DefaultMutableTreeNode rootNode = getRootNode();
    AbstractTreeUpdater updater = getUpdater();
    if (propertyName.equals(PsiTreeChangeEvent.PROP_ROOTS)) {
      updater.addSubtreeToUpdate(rootNode);
    }
    else if (propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
      if (!updater.addSubtreeToUpdateByElement(element) && element instanceof PsiJavaFile) {
        updater.addSubtreeToUpdateByElement(((PsiJavaFile)element).getContainingDirectory());
      }
    }
    else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME) || propertyName.equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)){
      if (element instanceof PsiDirectory && isFlattenPackages()){
        updater.addSubtreeToUpdate(rootNode);
        return;
      }

      updater.addSubtreeToUpdateByElement(element);
    }
    else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_TYPES)){
      updater.addSubtreeToUpdate(rootNode);
    }
  }
}
