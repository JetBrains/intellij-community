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
package com.intellij.lang.properties.projectView;

import com.intellij.ide.DeleteProvider;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ResourceBundleDeleteProvider implements DeleteProvider {
  private static final Logger LOG = Logger.getInstance(ResourceBundleDeleteProvider.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    final ResourceBundle[] resourceBundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    if (resourceBundles != null && resourceBundles.length != 0) {
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      LOG.assertTrue(project != null);

      final PsiElement[] toDelete = Arrays
        .stream(resourceBundles)
        .flatMap(rb -> rb.getPropertiesFiles().stream())
        .map(PropertiesFile::getContainingFile)
        .toArray(PsiElement[]::new);

      SafeDeleteHandler.invoke(project, toDelete, true, () -> {
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        for (ResourceBundle bundle : resourceBundles) {
          fileEditorManager.closeFile(new ResourceBundleAsVirtualFile(bundle));
        }
      });
    }
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    return project != null;
  }
}
