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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.edu.StudyDocumentListener;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.*;
import com.jetbrains.python.edu.editor.StudyEditor;

import java.io.*;

public class StudyRefreshTaskAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(StudyRefreshTaskAction.class.getName());

  public void refresh(final Project project) {
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
                StudyDocumentListener listener = StudyEditor.getListener(document);
                if (listener != null) {
                  document.removeDocumentListener(listener);
                }
                final int lineCount = document.getLineCount();
                if (lineCount != 0) {
                  CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
                    @Override
                    public void run() {
                      document.deleteString(0, document.getLineEndOffset(lineCount - 1));
                    }
                  });
                }
                StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
                Course course = taskManager.getCourse();
                assert course != null;
                File resourceFile = new File(course.getResourcePath());
                File resourceRoot = resourceFile.getParentFile();
                FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
                VirtualFile openedFile = fileDocumentManager.getFile(document);
                assert openedFile != null;
                final TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
                assert selectedTaskFile != null;
                Task currentTask = selectedTaskFile.getTask();
                String lessonDir = Lesson.LESSON_DIR + String.valueOf(currentTask.getLesson().getIndex() + 1);
                String taskDir = Task.TASK_DIR + String.valueOf(currentTask.getIndex() + 1);
                File pattern = new File(new File(new File(resourceRoot, lessonDir), taskDir), openedFile.getName());
                BufferedReader reader = null;
                try {
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
                  StudyStatus oldStatus = currentTask.getStatus();
                  LessonInfo lessonInfo = currentTask.getLesson().getLessonInfo();
                  lessonInfo.update(oldStatus, -1);
                  lessonInfo.update(StudyStatus.Unchecked, +1);
                  StudyUtils.updateStudyToolWindow(project);
                  for (TaskWindow taskWindow : selectedTaskFile.getTaskWindows()) {
                    taskWindow.reset();
                  }
                  ProjectView.getInstance(project).refresh();
                  if (listener != null) {
                    document.addDocumentListener(listener);
                  }
                  selectedTaskFile.drawAllWindows(editor);
                  IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
                  selectedTaskFile.navigateToFirstTaskWindow(editor);
                  BalloonBuilder balloonBuilder =
                    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("You can now start again", MessageType.INFO, null);
                  Balloon balloon = balloonBuilder.createBalloon();
                  StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
                  assert selectedStudyEditor != null;
                  balloon.showInCenterOf(selectedStudyEditor.getRefreshButton());
                }
                catch (FileNotFoundException e1) {
                  LOG.error(e1);
                }
                catch (IOException e1) {
                  LOG.error(e1);
                }
                finally {
                  StudyUtils.closeSilently(reader);
                }
              }
            });
          }
        });
  }

  public void actionPerformed(AnActionEvent e) {
    refresh(e.getProject());
  }
}
