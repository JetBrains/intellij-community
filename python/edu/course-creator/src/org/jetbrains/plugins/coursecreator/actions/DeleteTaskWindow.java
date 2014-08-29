package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.*;

import java.util.List;

@SuppressWarnings("ComponentNotRegistered")
public class DeleteTaskWindow extends DumbAwareAction {
  @NotNull
  private final TaskWindow myTaskWindow;

  public DeleteTaskWindow(@NotNull final TaskWindow taskWindow) {
    super("Delete task window","Delete task window", null);
    myTaskWindow = taskWindow;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file == null) return;
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) {
      return;
    }
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;

    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());
    final TaskFile taskFile = task.getTaskFile(file.getName());
    final List<TaskWindow> taskWindows = taskFile.getTaskWindows();
    if (taskWindows.contains(myTaskWindow)) {
      myTaskWindow.removeResources(project);
      taskWindows.remove(myTaskWindow);
      editor.getMarkupModel().removeAllHighlighters();
      CCProjectService.drawTaskWindows(file.getVirtualFile(), editor, course);
      DaemonCodeAnalyzerImpl.getInstance(project).restart(file);
    }
  }

}