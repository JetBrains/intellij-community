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
package org.jetbrains.idea.maven.dom.refactorings;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.references.MavenTargetUtil;

public class MavenPropertyRenameHandler extends PsiElementRenameHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext context) {
    return findTarget(context) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, PsiElement.EMPTY_ARRAY, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = findTarget(dataContext);

    RenameDialog dialog = new RenameDialog(project, element, null, PlatformDataKeys.EDITOR.getData(dataContext));
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String name = DEFAULT_NAME.getData(dataContext);
      dialog.performRename(name);
    }
    else {
      dialog.show();
    }
  }

  private PsiElement findTarget(DataContext context) {
    return MavenTargetUtil.getRefactorTarget(PlatformDataKeys.EDITOR.getData(context), LangDataKeys.PSI_FILE.getData(context));
  }
}
