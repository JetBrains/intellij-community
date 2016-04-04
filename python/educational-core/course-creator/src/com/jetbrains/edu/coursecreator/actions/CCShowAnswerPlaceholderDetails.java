package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.coursecreator.ui.CCCreateAnswerPlaceholderDialog;
import org.jetbrains.annotations.NotNull;

public class CCShowAnswerPlaceholderDetails extends CCAnswerPlaceholderAction {

  public CCShowAnswerPlaceholderDetails() {
    super("Edit Answer Placeholder", "Edit answer placeholder", null);
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    final Project project = state.getProject();
    PsiFile file = state.getFile();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;
    AnswerPlaceholder answerPlaceholder = state.getAnswerPlaceholder();
    CCCreateAnswerPlaceholderDialog dlg = new CCCreateAnswerPlaceholderDialog(project, answerPlaceholder
    );
    dlg.setTitle("Edit Answer Placeholder");
    dlg.show();
  }
}