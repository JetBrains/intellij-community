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
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleFromProjectViewRenameHandler implements RenameHandler {

  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }
    final ResourceBundle bundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);
    if (bundle == null || bundle.getPropertiesFiles().size() < 2) {
      return false;
    }
    return PlatformDataKeys.FILE_EDITOR.getData(dataContext) == null && CommonDataKeys.VIRTUAL_FILE.getData(dataContext) == null;
  }

  @Override
  public boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, final PsiFile file, DataContext dataContext) {
    final ResourceBundle resourceBundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);
    assert resourceBundle != null;
    RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, new PsiElement[] {resourceBundle.getDefaultPropertiesFile().getContainingFile()}, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }
}
