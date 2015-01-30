package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.format.*;
import com.jetbrains.edu.coursecreator.ui.CreateTaskWindowDialog;
import org.jetbrains.annotations.NotNull;

public class CCShowTaskWindowDetails extends CCTaskWindowAction {

  public CCShowTaskWindowDetails() {
    super("Edit Answer Placeholder", "Edit answer placeholder", null);
  }

  @Override
  protected void performTaskWindowAction(@NotNull CCState state) {
    final Project project = state.getProject();
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    PsiFile file = state.getFile();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;
    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());
    final TaskFile taskFile = state.getTaskFile();
    AnswerPlaceholder answerPlaceholder = state.getAnswerPlaceholder();
    CreateTaskWindowDialog dlg = new CreateTaskWindowDialog(project, answerPlaceholder, lesson.getIndex(), task.getIndex(),
                                                            file.getVirtualFile().getNameWithoutExtension(),
                                                            taskFile.getTaskWindows().size() + 1);
    dlg.setTitle("Edit Answer Placeholder");
    dlg.show();
  }
}