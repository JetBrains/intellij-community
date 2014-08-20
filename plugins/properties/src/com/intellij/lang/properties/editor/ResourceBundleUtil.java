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
 * @since 10/5/11 2:35 PM
 */
public class ResourceBundleUtil {

  private ResourceBundleUtil() {
  }

  /**
   * Tries to derive {@link com.intellij.lang.properties.ResourceBundle resource bundle} related to the given context.
   *
   * @param dataContext   target context
   * @return              {@link com.intellij.lang.properties.ResourceBundle resource bundle} related to the given context if any;
   *                      <code>null</code> otherwise
   */
  @Nullable
  public static ResourceBundle getResourceBundleFromDataContext(@NotNull DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof IProperty) return null; //rename property
    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    if (bundles != null && bundles.length == 1) return bundles[0];
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null) {
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
