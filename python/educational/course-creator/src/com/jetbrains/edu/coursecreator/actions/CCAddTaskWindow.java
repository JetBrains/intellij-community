package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.format.*;
import com.jetbrains.edu.coursecreator.ui.CreateTaskWindowDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCAddTaskWindow extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCAddTaskWindow.class);

  public CCAddTaskWindow() {
    super("Add Answer Placeholder", "Add answer placeholder", null);
  }


  private static boolean areTaskWindowsIntersect(@NotNull final TaskFile taskFile, @NotNull final Document document, int start, int end) {
    List<AnswerPlaceholder> answerPlaceholders = taskFile.getTaskWindows();
    for (AnswerPlaceholder existingAnswerPlaceholder : answerPlaceholders) {
      int twStart = existingAnswerPlaceholder.getRealStartOffset(document);
      int twEnd = existingAnswerPlaceholder.getReplacementLength() + twStart;
      if ((start >= twStart && start < twEnd) || (end > twStart && end <= twEnd) ||
          (twStart >= start && twStart < end) || (twEnd > start && twEnd <= end)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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
    if (taskFile == null) {
      return;
    }
    if (areTaskWindowsIntersect(taskFile, document, start, end)) {
      return;
    }
    final AnswerPlaceholder answerPlaceholder = new AnswerPlaceholder(lineNumber, realStart, length, model.getSelectedText());
    CreateTaskWindowDialog dlg = new CreateTaskWindowDialog(project, answerPlaceholder, lesson.getIndex(),
                                                            task.getIndex(), file.getVirtualFile().getNameWithoutExtension(),
                                                            taskFile.getTaskWindows().size() + 1);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    int index = taskFile.getTaskWindows().size() + 1;
    taskFile.addTaskWindow(answerPlaceholder, index);
    taskFile.sortTaskWindows();
    answerPlaceholder.drawHighlighter(editor, false);
    answerPlaceholder.createGuardedBlocks(editor);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!CCProjectService.setCCActionAvailable(event)) {
      return;
    }
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
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    int start = selectionModel.getSelectionStart();
    int end = selectionModel.getSelectionEnd();

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final PsiDirectory taskDir = file.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;

    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    final Task task = lesson.getTask(taskDir.getName());
    if (task == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    TaskFile taskFile = task.getTaskFile(file.getName());
    if (taskFile == null) {
      LOG.info("could not find task file");
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    if (areTaskWindowsIntersect(taskFile, editor.getDocument(), start, end)) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);
  }
}