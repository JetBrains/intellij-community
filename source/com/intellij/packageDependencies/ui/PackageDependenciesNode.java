package com.intellij.packageDependencies.ui;

import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.HashSet;
import java.util.Set;

public class PackageDependenciesNode extends DefaultMutableTreeNode {
  private static final EmptyIcon EMPTY_ICON = EmptyIcon.create(0, IconUtilEx.getEmptyIcon(false).getIconHeight());
  private Set<PsiFile> myRegisteredFiles = new HashSet<PsiFile>();
  private boolean myHasUnmarked = false;
  private boolean myHasMarked = false;

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    set.addAll(myRegisteredFiles);
  }

  public void addFile(PsiFile file, boolean isMarked) {
    myRegisteredFiles.add(file);
    updateMarked(!isMarked, isMarked);
  }

  public Icon getOpenIcon() {
    return EMPTY_ICON;
  }

  public Icon getClosedIcon() {
    return EMPTY_ICON;
  }

  public int getWeight() {
    return 0;
  }

  public boolean hasUnmarked() {
    return myHasUnmarked;
  }

  public boolean hasMarked() {
    return myHasMarked;
  }

  public PsiElement getPsiElement() {
    return null;
  }

  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    boolean hasUnmarked = ((PackageDependenciesNode)newChild).hasUnmarked();
    boolean hasMarked = ((PackageDependenciesNode)newChild).hasMarked();
    updateMarked(hasUnmarked, hasMarked);
  }

  private void updateMarked(boolean hasUnmarked, boolean hasMarked) {
    if (hasUnmarked && !myHasUnmarked || hasMarked && !myHasMarked) {
      myHasUnmarked |= hasUnmarked;
      myHasMarked |= hasMarked;
      PackageDependenciesNode parent = ((PackageDependenciesNode)getParent());
      if (parent != null) {
        parent.updateMarked(myHasUnmarked, myHasMarked);
      }
    }
  }
}