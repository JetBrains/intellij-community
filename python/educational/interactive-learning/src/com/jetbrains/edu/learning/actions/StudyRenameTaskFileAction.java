package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.actions.RenameElementAction;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

public class StudyRenameTaskFileAction extends RenameElementAction {
  @Override
  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    return super.isEnabledOnDataContext(dataContext) && !isTaskFile(element);
  }

  private static boolean isTaskFile(PsiElement element) {
    return element != null && StudyUtils.getTaskFile(element.getProject(),
                                  element.getContainingFile().getVirtualFile()) != null;
  }

  @Override
  public boolean isEnabledOnElements(@NotNull PsiElement[] elements){
    return !super.isEnabledOnElements(elements) && !isTaskFile(elements[0]);
  }
}
