package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.*;
import org.jetbrains.plugins.coursecreator.ui.CreateTaskWindowDialog;

@SuppressWarnings("ComponentNotRegistered")
public class ShowTaskWindowText extends DumbAwareAction {
  @NotNull
  private final TaskWindow myTaskWindow;

  public ShowTaskWindowText(@NotNull final TaskWindow taskWindow) {
    super("Add task window","Add task window", null);
    myTaskWindow = taskWindow;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file == null) return;
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;

    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());
    final TaskFile taskFile = task.getTaskFile(file.getName());
    //TODO: copy task window and return if modification canceled
    CreateTaskWindowDialog dlg = new CreateTaskWindowDialog(project, myTaskWindow, lesson.getIndex(), task.getIndex(), file.getVirtualFile().getNameWithoutExtension(), taskFile.getTaskWindows().size() + 1);
    dlg.show();
  }
}