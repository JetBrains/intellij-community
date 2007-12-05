package com.intellij.psi.impl;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.util.Icons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class DirectoryIconProvider implements IconProvider {
  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof PsiDirectory) {
      Icon symbolIcon;
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      if (JavaDirectoryService.getInstance().getPackage(psiDirectory) != null) {
        symbolIcon = Icons.PACKAGE_ICON;
      }
      else {
        symbolIcon = Icons.DIRECTORY_CLOSED_ICON;
      }
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      final Project project = psiDirectory.getProject();
      boolean isExcluded = ElementPresentationUtil.isExcluded(vFile, project);
      return ElementBase.createLayeredIcon(symbolIcon, isExcluded ? ElementPresentationUtil.FLAGS_EXCLUDED : 0);
    }
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
