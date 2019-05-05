package com.intellij.bash;

import com.intellij.bash.psi.ShFile;
import com.intellij.bash.rename.ShSelectAllOccurrencesHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;

public class ShRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    return dataContext.getData(CommonDataKeys.PSI_FILE) instanceof ShFile;
  }

  @Override
  public boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    ShSelectAllOccurrencesHandler.INSTANCE.execute(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }
}
