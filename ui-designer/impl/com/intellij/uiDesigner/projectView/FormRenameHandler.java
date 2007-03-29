/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 26.10.2006
 * Time: 16:44:00
 */
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
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    return forms != null && forms.length == 1;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    if (forms == null || forms.length != 1) return;
    PsiClass boundClass = forms [0].getClassToBind();
    RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, new PsiElement[] { boundClass },
                                                                               dataContext);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }
}