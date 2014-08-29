package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.*;
import org.jetbrains.plugins.coursecreator.ui.CreateTaskWindowDialog;

public class AddTaskWindow extends DumbAwareAction {
  public AddTaskWindow() {
    super("Add task window","Add task window", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file == null) return;
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) return;

    final SelectionModel model = editor.getSelectionModel();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    final int start = model.getSelectionStart();
    final int end = model.getSelectionEnd();
    final int lineNumber = document.getLineNumber(start);
    final int length = end - start;
    int realStart = start - document.getLineStartOffset(lineNumber);

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;

    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());
    final TaskFile taskFile = task.getTaskFile(file.getName());
    final TaskWindow taskWindow = new TaskWindow(lineNumber, realStart, length, model.getSelectedText());
    CreateTaskWindowDialog dlg = new CreateTaskWindowDialog(project, taskWindow, lesson.getIndex(), task.getIndex(), file.getVirtualFile().getNameWithoutExtension(), taskFile.getTaskWindows().size() + 1);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    int index = taskFile.getTaskWindows().size() + 1;
    taskFile.addTaskWindow(taskWindow, index);
    taskWindow.drawHighlighter(editor);
    DaemonCodeAnalyzerImpl.getInstance(project).restart(file);
  }

  @Override
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    final Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(event.getDataContext());
    if (editor == null || file == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    if (!editor.getSelectionModel().hasSelection()) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;

    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());
    if (task == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);

  }
}