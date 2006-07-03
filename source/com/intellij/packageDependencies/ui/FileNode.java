package com.intellij.packageDependencies.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.IconUtil;

import javax.swing.*;
import java.util.Set;

public class FileNode extends PackageDependenciesNode {
  private PsiFile myFile;
  private boolean myMarked;
  private static final Logger LOG = Logger.getInstance("com.intellij.packageDependencies.ui.FileNode");

  public FileNode(PsiFile file, boolean marked) {
    myFile = file;
    myMarked = marked;
    setUserObject(toString());
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
    final VirtualFile virtualFile = myFile.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    return virtualFile.getName();
  }

  public Icon getOpenIcon() {
    return getIcon();
  }

  public Icon getClosedIcon() {
    return getIcon();
  }

  private Icon getIcon() {
    VirtualFile vFile = myFile.getVirtualFile();
    LOG.assertTrue(vFile != null);
    return IconUtil.getIcon(vFile, Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS, myFile.getProject());
  }

  public int getWeight() {
    return 4;
  }

  public int getContainingFiles() {
    return 1;
  }

  public PsiElement getPsiElement() {
    return myFile;
  }

  public FileStatus getStatus() {
    return FileStatusManager.getInstance(myFile.getProject()).getStatus(myFile.getVirtualFile());
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof FileNode)) return false;

    final FileNode fileNode = (FileNode)o;

    if (!myFile.equals(fileNode.myFile)) return false;

    return true;
  }

  public int hashCode() {
    return myFile.hashCode();
  }

  public String getFQName(final boolean fileSeparator) {
    StringBuffer buf = new StringBuffer(20);
    if (myFile instanceof PsiJavaFile && !fileSeparator) {
      String packageName = ((PsiJavaFile)myFile).getPackageName();
      buf.append(packageName);
      if (buf.length() > 0) {
        buf.append('.');
      }
      final VirtualFile virtualFile = myFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      buf.append(virtualFile.getNameWithoutExtension());
      return buf.toString();
    } else {
      final VirtualFile virtualFile = myFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      final VirtualFile contentRoot =
      ProjectRootManager.getInstance(myFile.getProject()).getFileIndex().getContentRootForFile(virtualFile);
      return VfsUtil.getRelativePath(virtualFile, contentRoot, '/');
    }
  }
}