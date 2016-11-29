/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    myExtensions = new HashSet<>(Arrays.asList(formatNames));
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
