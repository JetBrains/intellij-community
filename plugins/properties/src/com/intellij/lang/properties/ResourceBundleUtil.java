/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.editor.ResourceBundleEditor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains utility methods for resource bundle-related processing.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/9/10 5:19 PM
 */
public class ResourceBundleUtil {

  private ResourceBundleUtil() {
  }

  /**
   * Tries to derive {@link ResourceBundle resource bundle} related to the given context.
   * 
   * @param dataContext   target context
   * @return              {@link ResourceBundle resource bundle} related to the given context if any; <code>null</code> otherwise
   */
  @Nullable
  public static ResourceBundle getResourceBundleFromDataContext(@NotNull DataContext dataContext) {
    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof IProperty) return null; //rename property
    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    if (bundles != null && bundles.length == 1) return bundles[0];
    VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null) {
      return null;
    }
    if (virtualFile instanceof ResourceBundleAsVirtualFile) {
      return ((ResourceBundleAsVirtualFile)virtualFile).getResourceBundle();
    }
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) {
        return ((PropertiesFile)psiFile).getResourceBundle();
      }
    }
    return null;
  }

  /**
   * Tries to derive {@link ResourceBundleEditor resource bundle editor} identified by the given context. 
   * 
   * @param dataContext     target data context
   * @return                resource bundle editor identified by the given context; <code>null</code> otherwise
   */
  @Nullable
  public static ResourceBundleEditor getEditor(@NotNull DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    
    VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null) {
      return null;
    }
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(virtualFile);
    if (editors.length != 1 || (!(editors[0] instanceof ResourceBundleEditor))) {
      return null;
    }

    return (ResourceBundleEditor)editors[0];
  }
}
