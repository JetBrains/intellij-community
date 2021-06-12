// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;


public class PyDirectoryIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      // Preserve original icons for excluded directories and source roots
      if (isSpecialDirectory(directory)) return null;
      if (PyUtil.isExplicitPackage(directory)) {
        return PlatformIcons.PACKAGE_ICON;
      }
    }
    return null;
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
