package com.intellij.bash.rename;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class BashRenameIntention extends BaseIntentionAction {
  public BashRenameIntention() {
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @NotNull
  @Override
  public String getText() {
    return "Rename text under caret";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return BashSelectAllOccurrencesHandler.INSTANCE.isEnabled(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    BashSelectAllOccurrencesHandler.INSTANCE.execute(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }
}
