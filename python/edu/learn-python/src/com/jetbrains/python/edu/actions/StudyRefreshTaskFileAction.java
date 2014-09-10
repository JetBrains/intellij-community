package com.jetbrains.python.edu.actions;

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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.edu.StudyDocumentListener;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.*;
import com.jetbrains.python.edu.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class StudyRefreshTaskFileAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(StudyRefreshTaskFileAction.class.getName());

  public static void refresh(final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
          @Override
          public void run() {
            final Editor editor = StudyEditor.getSelectedEditor(project);
            assert editor != null;
            final Document document = editor.getDocument();
            refreshFile(editor, document, project);
          }
        });
      }
    });
  }

  public static void refreshFile(@NotNull final Editor editor, @NotNull final Document document, @NotNull final Project project) {
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    Course course = taskManager.getCourse();
    assert course != null;
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile openedFile = fileDocumentManager.getFile(document);
    assert openedFile != null;
    final TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
    assert selectedTaskFile != null;
    String openedFileName = openedFile.getName();
    Task currentTask = selectedTaskFile.getTask();
    resetTaskFile(document, project, course, selectedTaskFile, openedFileName, currentTask);
    selectedTaskFile.drawAllWindows(editor);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
      }
    });
    selectedTaskFile.navigateToFirstTaskWindow(editor);
    showBaloon(project);
  }

  public static void resetTaskFile(Document document, Project project, Course course, TaskFile taskFile, String name, Task task) {
    resetDocument(document, course, name, task);
    updateLessonInfo(task);
    StudyUtils.updateStudyToolWindow(project);
    resetTaskWindows(taskFile);
    ProjectView.getInstance(project).refresh();
  }

  private static void showBaloon(Project project) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("You can now start again", MessageType.INFO, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
    assert selectedStudyEditor != null;
    balloon.showInCenterOf(selectedStudyEditor.getRefreshButton());
    Disposer.register(project, balloon);
  }

  private static void resetTaskWindows(TaskFile selectedTaskFile) {
    for (TaskWindow taskWindow : selectedTaskFile.getTaskWindows()) {
      taskWindow.reset();
    }
  }

  private static void updateLessonInfo(Task currentTask) {
    StudyStatus oldStatus = currentTask.getStatus();
    LessonInfo lessonInfo = currentTask.getLesson().getLessonInfo();
    lessonInfo.update(oldStatus, -1);
    lessonInfo.update(StudyStatus.Unchecked, +1);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static void resetDocument(Document document, Course course, String fileName, Task task) {
    BufferedReader reader = null;
    StudyDocumentListener listener = StudyEditor.getListener(document);
    if (listener != null) {
      document.removeDocumentListener(listener);
    }
    clearDocument(document);
    try {
      String lessonDir = Lesson.LESSON_DIR + String.valueOf(task.getLesson().getIndex() + 1);
      String taskDir = Task.TASK_DIR + String.valueOf(task.getIndex() + 1);
      File resourceFile = new File(course.getResourcePath());
      File resourceRoot = resourceFile.getParentFile();
      File pattern = new File(new File(new File(resourceRoot, lessonDir), taskDir), fileName);
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(pattern)));
      String line;
      StringBuilder patternText = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        patternText.append(line);
        patternText.append("\n");
      }
      int patternLength = patternText.length();
      if (patternText.charAt(patternLength - 1) == '\n') {
        patternText.delete(patternLength - 1, patternLength);
      }
      document.setText(patternText);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    if (listener != null) {
      document.addDocumentListener(listener);
    }
  }

  private static void clearDocument(final Document document) {
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

  public void actionPerformed(@NotNull AnActionEvent e) {
    refresh(e.getProject());
  }
}
