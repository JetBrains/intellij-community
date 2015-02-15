package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.ui.CCCreateAnswerPlaceholderDialog;
import org.jetbrains.annotations.NotNull;

public class CCShowAnswerPlaceholderDetails extends CCAnswerPlaceholderAction {

  public CCShowAnswerPlaceholderDetails() {
    super("Edit Answer Placeholder", "Edit answer placeholder", null);
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    final Project project = state.getProject();
    final CCProjectService service = CCProjectService.getInstance(project);
    PsiFile file = state.getFile();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;
    final Lesson lesson = service.getLesson(lessonDir.getName());
    final Task task = service.getTask(taskDir.getVirtualFile().getPath());
    final TaskFile taskFile = state.getTaskFile();
    AnswerPlaceholder answerPlaceholder = state.getAnswerPlaceholder();
    CCCreateAnswerPlaceholderDialog dlg = new CCCreateAnswerPlaceholderDialog(project, answerPlaceholder
    );
    dlg.setTitle("Edit Answer Placeholder");
    dlg.show();
  }
}