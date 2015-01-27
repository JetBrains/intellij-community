package com.jetbrains.edu.learning.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.edu.learning.StudyNames;
import com.intellij.problems.WolfTheProblemSolver;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.course.*;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class StudyRefreshTaskFileAction extends DumbAwareAction {
  public static final String ACTION_ID = "RefreshTaskAction";
  public static final String SHORTCUT = "ctrl shift pressed X";
  private static final Logger LOG = Logger.getInstance(StudyRefreshTaskFileAction.class.getName());

  public static void refresh(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
          @Override
          public void run() {
            StudyEditor studyEditor = StudyEditor.getSelectedStudyEditor(project);
            StudyState studyState = new StudyState(studyEditor);
            if (studyEditor == null || !studyState.isValid()) {
              LOG.info("RefreshTaskFileAction was invoked outside of Study Editor");
              return;
            }
            refreshFile(studyState, project);
          }
        });
      }
    });
  }

  private static void refreshFile(@NotNull final StudyState studyState, @NotNull final Project project) {
    final Editor editor = studyState.getEditor();
    final TaskFile taskFile = studyState.getTaskFile();
    if (!resetTaskFile(editor.getDocument(), project, taskFile, studyState.getVirtualFile().getName())) {
      return;
    }
    WolfTheProblemSolver.getInstance(project).clearProblems(studyState.getVirtualFile());
    taskFile.setHighlightErrors(false);
    taskFile.drawAllWindows(editor);
    taskFile.createGuardedBlocks(editor);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
      }
    });
    taskFile.navigateToFirstTaskWindow(editor);
    showBalloon(project, "You can start again now", MessageType.INFO);
  }

  private static boolean resetTaskFile(@NotNull final Document document,
                                       @NotNull final Project project,
                                       TaskFile taskFile,
                                       String name) {
    if (!resetDocument(project, document, taskFile, name)) {
      return false;
    }
    updateLessonInfo(taskFile.getTask());
    StudyUtils.updateStudyToolWindow(project);
    resetTaskWindows(taskFile);
    ProjectView.getInstance(project).refresh();
    return true;
  }

  private static void showBalloon(@NotNull final Project project, String text, @NotNull final MessageType messageType) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, messageType, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
    assert selectedStudyEditor != null;
    balloon.showInCenterOf(selectedStudyEditor.getRefreshButton());
    Disposer.register(project, balloon);
  }

  private static void resetTaskWindows(TaskFile selectedTaskFile) {
    for (AnswerPlaceholder answerPlaceholder : selectedTaskFile.getAnswerPlaceholders()) {
      answerPlaceholder.reset();
    }
  }

  private static void updateLessonInfo(Task currentTask) {
    StudyStatus oldStatus = currentTask.getStatus();
    LessonInfo lessonInfo = currentTask.getLesson().getLessonInfo();
    lessonInfo.update(oldStatus, -1);
    lessonInfo.update(StudyStatus.Unchecked, +1);
  }

  private static boolean resetDocument(@NotNull final Project project,
                                       @NotNull final Document document,
                                       @NotNull final TaskFile taskFile,
                                       String fileName) {
    StudyEditor.deleteGuardedBlocks(document);
    taskFile.setTrackChanges(false);
    clearDocument(document);
    Task task = taskFile.getTask();
    String lessonDir = StudyNames.LESSON_DIR + String.valueOf(task.getLesson().getIndex() + 1);
    String taskDir = Task.TASK_DIR + String.valueOf(task.getIndex() + 1);
    Course course = task.getLesson().getCourse();
    File resourceFile = new File(course.getCourseDirectory());
    if (!resourceFile.exists()) {
      showBalloon(project, "Course was deleted", MessageType.ERROR);
      return false;
    }
    String patternPath = FileUtil.join(resourceFile.getPath(), lessonDir, taskDir, fileName);
    VirtualFile patternFile = VfsUtil.findFileByIoFile(new File(patternPath), true);
    if (patternFile == null) {
      return false;
    }
    final Document patternDocument = FileDocumentManager.getInstance().getDocument(patternFile);
    if (patternDocument == null) {
      return false;
    }
    document.setText(patternDocument.getCharsSequence());
    taskFile.setTrackChanges(true);
    return true;
  }

  private static void clearDocument(@NotNull final Document document) {
    final int lineCount = document.getLineCount();
    if (lineCount != 0) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          document.deleteString(0, document.getLineEndOffset(lineCount - 1));
        }
      });
    }
  }

  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project != null) {
      refresh(project);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    final Project project = event.getProject();
    if (project != null) {
      StudyEditor studyEditor = StudyEditor.getSelectedStudyEditor(project);
      StudyState studyState = new StudyState(studyEditor);
      if (studyState.isValid()) {
        StudyUtils.enableAction(event, true);
      }
    }
    StudyUtils.enableAction(event, false);
  }
}
