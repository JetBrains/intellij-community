/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.psi.PyUtil;
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
      if (PyUtil.isPackage(directory, null) && !isSpecialDirectory(directory)) {
        return PlatformIcons.PACKAGE_ICON;
      }
    }
    return null;
  }

  private static boolean isSpecialDirectory(@NotNull PsiDirectory directory) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(directory);
    final VirtualFile vFile = directory.getVirtualFile();
    // If module is null, directory is probably excluded
    return module == null || PyUtil.getSourceRoots(module).contains(vFile);
  }
}
