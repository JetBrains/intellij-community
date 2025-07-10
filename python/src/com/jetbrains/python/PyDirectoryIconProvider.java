// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public final class PyDirectoryIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PsiDirectory directory) {
      if (isMultimoduleProjectDetectionEnabled()) {
        if (ProjectRootsUtil.isModuleContentRoot(directory.getVirtualFile(), directory.getProject())) {
          return AllIcons.Nodes.Module;
        }
      }
      // Preserve original icons for excluded directories and source roots
      if (isSpecialDirectory(directory)) return null;
      if (PyUtil.isExplicitPackage(directory)) {
        return AllIcons.Nodes.Package;
      }
    }
    return null;
  }

  private static boolean isMultimoduleProjectDetectionEnabled() {
    return Registry.is("python.project.model.uv") || Registry.is("python.project.model.poetry");
  }

  private static boolean isSpecialDirectory(@NotNull PsiDirectory directory) {
    final VirtualFile vFile = directory.getVirtualFile();
    if (FileIndexFacade.getInstance(directory.getProject()).isExcludedFile(vFile)) {
      return true;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(directory);
    return module == null || PyUtil.getSourceRoots(module).contains(vFile);
  }
}
