// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;


public final class PyDirectoryIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PsiDirectory directory) {
      if (ProjectRootsUtil.isModuleContentRoot(directory.getVirtualFile(), directory.getProject())) {
        return AllIcons.Nodes.Module;
      }
      // Preserve original icons for excluded directories and source roots
      if (isSpecialDirectory(directory)) return null;
      if (PyUtil.isExplicitPackage(directory)) {
        return AllIcons.Nodes.Package;
      }
    }
    return null;
  }


  private static boolean isSpecialDirectory(@NotNull PsiDirectory directory) {
    final VirtualFile vFile = directory.getVirtualFile();
    if (FileIndexFacade.getInstance(directory.getProject()).isExcludedFile(vFile)) {
      return true;
    }

    // Check whether directory is a source- or content-root
    // On large projects, using the ProjectFileIndex here is *noticeably* faster than
    // asking for all source- and content-roots and checking .contains(vFile)
    return ProjectRootsUtil.isSourceRoot(directory) || ProjectRootsUtil.isModuleContentRoot(directory);
  }
}
