/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner;

import com.intellij.ide.util.TreeFileChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.Set;

/**
 * @author yole
 */
public class ImageFileFilter implements TreeFileChooser.PsiFileFilter {
  private final Set<String> myExtensions;
  private GlobalSearchScope myModuleScope;

  public ImageFileFilter(@Nullable Module module) {
    final String[] formatNames = ImageIO.getReaderFormatNames();
    for(int i=0; i<formatNames.length; i++) {
      formatNames [i] = formatNames [i].toLowerCase();
    }
    myExtensions = new HashSet<String>(Arrays.asList(formatNames));
    if (module != null) {
      myModuleScope = module.getModuleWithDependenciesAndLibrariesScope(true);
    }
  }

  public boolean accept(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      String extension = virtualFile.getExtension();
      return extension != null &&
             myExtensions.contains(extension.toLowerCase()) &&
             (myModuleScope == null || myModuleScope.contains(virtualFile));
    }
    return false;
  }
}
