package com.intellij.packageDependencies.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

import javax.swing.*;
import java.util.Set;

public class FileNode extends PackageDependenciesNode {
  private PsiFile myFile;
  private boolean myMarked;

  public FileNode(PsiFile file, boolean marked) {
    myFile = file;
    myMarked = marked;
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    set.add(myFile);
  }

  public boolean hasUnmarked() {
    return !myMarked;
  }

  public boolean hasMarked() {
    return myMarked;
  }

  public String toString() {
    return myFile.getVirtualFile().getName();
  }

  public Icon getOpenIcon() {
    return getIcon();
  }

  public Icon getClosedIcon() {
    return getIcon();
  }

  private Icon getIcon() {
    VirtualFile vFile = myFile.getVirtualFile();
    return vFile.getIcon();
  }

  public int getWeight() {
    return 4;
  }

  public PsiElement getPsiElement() {
    return myFile;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileNode)) return false;

    final FileNode fileNode = (FileNode)o;

    if (!myFile.equals(fileNode.myFile)) return false;

    return true;
  }

  public int hashCode() {
    return myFile.hashCode();
  }

  public String getFQName() {
    if (myFile instanceof PsiJavaFile) {
      StringBuffer buf = new StringBuffer(20);
      String packageName = ((PsiJavaFile)myFile).getPackageName();
      if (packageName != null) {
        buf.append(packageName);
      }
      if (buf.length() > 0) {
        buf.append('.');
      }
      buf.append(myFile.getVirtualFile().getNameWithoutExtension());
      return buf.toString();
    }

    return null;
  }
}