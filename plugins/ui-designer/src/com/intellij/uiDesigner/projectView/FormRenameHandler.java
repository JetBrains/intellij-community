// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.projectView;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;

public class FormRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    return forms != null && forms.length == 1;
  }

  @Override
  public boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    if (forms == null || forms.length != 1) return;
    PsiClass boundClass = forms [0].getClassToBind();
    RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, new PsiElement[] { boundClass },
                                                                               dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }
}