
package com.intellij.packageDependencies.ui;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.Set;

public class DirectoryNode extends PackageDependenciesNode {

  private String myDirName;
  private final PsiDirectory myDirectory;


  public DirectoryNode(PsiDirectory aDirectory) {
    myDirectory = aDirectory;
    myDirName = aDirectory.getName();
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      if (child instanceof FileNode || recursively) {
        child.fillFiles(set, true);
      }
    }
  }

  public String toString() {
    return myDirName;
  }

  public String getDirName(){
    final VirtualFile contentRoot =
      ProjectRootManager.getInstance(myDirectory.getProject()).getFileIndex().getContentRootForFile(myDirectory.getVirtualFile());
    return VfsUtil.getRelativePath(myDirectory.getVirtualFile(), contentRoot, '/');    
  }

  public PsiElement getPsiElement() {
    return myDirectory;
  }

  public int getWeight() {
    return 3;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof DirectoryNode)) return false;

    final DirectoryNode packageNode = (DirectoryNode)o;

    if (!myDirName.equals(packageNode.myDirName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myDirName.hashCode();
    return result;
  }

  public Icon getOpenIcon() {
    return Icons.PACKAGE_OPEN_ICON;
  }

  public Icon getClosedIcon() {
    return Icons.PACKAGE_ICON;
  }
}
