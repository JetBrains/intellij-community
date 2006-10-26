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

import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FormRenameHandler implements RenameHandler, ApplicationComponent {
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Form[] forms = (Form[]) dataContext.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    return forms != null && forms.length == 1;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    Form[] forms = (Form[]) dataContext.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    if (forms == null || forms.length != 1) return;
    PsiClass boundClass = forms [0].getClassToBind();
    RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, new PsiElement[] { boundClass },
                                                                               dataContext);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FormRenameHandler";
  }

  public void initComponent() {
    RenameHandlerRegistry.getInstance().registerHandler(this);
  }

  public void disposeComponent() {
  }
}