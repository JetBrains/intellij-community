// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 */
public final class ResourceBundleUtil {

  private ResourceBundleUtil() {
  }

  /**
   * Tries to derive {@link ResourceBundle resource bundle} related to the given context.
   *
   * @param dataContext   target context
   * @return              {@link ResourceBundle resource bundle} related to the given context if any;
   *                      {@code null} otherwise
   */
  @Nullable
  public static ResourceBundle getResourceBundleFromDataContext(@NotNull DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof IProperty) return null; //rename property
    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    if (bundles != null && bundles.length == 1) return bundles[0];
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (virtualFile instanceof ResourceBundleAsVirtualFile && project != null) {
      return ((ResourceBundleAsVirtualFile)virtualFile).getResourceBundle();
    }
    if (project != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) {
        return ((PropertiesFile)psiFile).getResourceBundle();
      }
    }
    return null;
  }
}
