package com.intellij.cyclicDependencies.ui;

import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Set;
import java.util.Iterator;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CycleNode extends PackageDependenciesNode{
  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
  }

  public void addFile(PsiFile file, boolean isMarked) {
    super.addFile(file, isMarked);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public Icon getOpenIcon() {
    return super.getOpenIcon();
  }

  public Icon getClosedIcon() {
    return super.getClosedIcon();
  }

  public int getWeight() {
    return super.getWeight();
  }

  public String toString() {
    return "cycle";
  }

}
