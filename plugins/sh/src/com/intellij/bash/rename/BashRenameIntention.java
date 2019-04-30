package com.intellij.bash.rename;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class BashRenameIntention extends BaseIntentionAction {

  private BashSelectAllOccurrencesAction mySelectAllOccurrencesAction;

  public BashRenameIntention() {
  }

  @NotNull
  private EditorAction getAction() {
    if (mySelectAllOccurrencesAction == null) {
      mySelectAllOccurrencesAction = BashSelectAllOccurrencesAction.findAction();
    }
    return mySelectAllOccurrencesAction;
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
    EditorAction action = getAction();
    return action.getHandler().isEnabled(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    EditorAction action = getAction();
    action.getHandler().execute(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }
}
