package com.jetbrains.python;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PyDirectoryIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
        final VirtualFile vFile = directory.getVirtualFile();
        final VirtualFile root = ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getSourceRootForFile(vFile);
        if (root != vFile) {
          return (flags & Iconable.ICON_FLAG_OPEN) != 0 ? PlatformIcons.PACKAGE_OPEN_ICON : PlatformIcons.PACKAGE_ICON;
        }
      }
    }
    return null;
  }
}
