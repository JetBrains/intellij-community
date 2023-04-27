// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner;

import com.intellij.ide.util.TreeFileChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.util.Set;


public class ImageFileFilter implements TreeFileChooser.PsiFileFilter {
  private final Set<String> myExtensions;
  private GlobalSearchScope myModuleScope;

  public ImageFileFilter(@Nullable Module module) {
    final String[] formatNames = ImageIO.getReaderFormatNames();
    myExtensions = ContainerUtil.map2Set(formatNames, formatName->StringUtil.toLowerCase(formatName));
    if (module != null) {
      myModuleScope = module.getModuleWithDependenciesAndLibrariesScope(true);
    }
  }

  @Override
  public boolean accept(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      String extension = virtualFile.getExtension();
      return extension != null &&
             myExtensions.contains(StringUtil.toLowerCase(extension)) &&
             (myModuleScope == null || myModuleScope.contains(virtualFile));
    }
    return false;
  }
}
