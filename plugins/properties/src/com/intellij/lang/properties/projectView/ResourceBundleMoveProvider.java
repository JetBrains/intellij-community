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
package com.intellij.lang.properties.projectView;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.MoveAction;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Aug 26, 2010
 */
public class ResourceBundleMoveProvider implements MoveAction.MoveProvider, RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#" + ResourceBundleMoveProvider.class.getName());

  @Override
  public boolean isEnabledOnDataContext(DataContext dataContext) {
    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    return bundles != null;
  }

  @Override
  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return this;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.info("invoked on file");
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    final Set<PsiElement> filesOrDirs = new HashSet<PsiElement>();
    for (PsiElement element : elements) {
      final PsiFile file = element.getContainingFile();
      if (file != null) {
        filesOrDirs.add(file);
      } else if (element instanceof PsiDirectory) {
        filesOrDirs.add(element);
      }
    }

    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    LOG.assertTrue(bundles != null);
    for (ResourceBundle bundle : bundles) {
      filesOrDirs.addAll(bundle.getPropertiesFiles(project));
    }

    final PsiElement initialTargetElement = LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext);
    MoveFilesOrDirectoriesUtil
      .doMove(project, filesOrDirs.toArray(new PsiElement[filesOrDirs.size()]), new PsiElement[]{initialTargetElement}, null);
  }
}
