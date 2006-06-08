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
  private Set<String> myExtensions;
  private GlobalSearchScope myModuleScope;

  public ImageFileFilter(@Nullable Module module) {
    myExtensions = new HashSet<String>(Arrays.asList(ImageIO.getReaderFormatNames()));
    if (module != null) {
      myModuleScope = module.getModuleWithDependenciesAndLibrariesScope(true);
    }
  }

  public boolean accept(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null &&
           myExtensions.contains(virtualFile.getExtension()) &&
           (myModuleScope == null || myModuleScope.contains(virtualFile));
  }
}
