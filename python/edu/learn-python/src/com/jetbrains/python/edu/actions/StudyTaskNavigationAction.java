package com.jetbrains.python.edu.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.course.Lesson;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.editor.StudyEditor;

import javax.swing.*;
import java.util.Map;

/**
 * author: liana
 * data: 7/21/14.
 */
abstract public class StudyTaskNavigationAction extends DumbAwareAction {
  public void navigateTask(Project project) {
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assert selectedEditor != null;
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    assert openedFile != null;
    TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
    assert selectedTaskFile != null;
    Task currentTask = selectedTaskFile.getTask();
    Task nextTask = getTargetTask(currentTask);
    if (nextTask == null) {
      BalloonBuilder balloonBuilder =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(getNavigationFinishedMessage(), MessageType.INFO, null);
      Balloon balloon = balloonBuilder.createBalloon();
      StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
      balloon.showInCenterOf(getButton(selectedStudyEditor));
      return;
    }
    for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
      FileEditorManager.getInstance(project).closeFile(file);
    }
    int nextTaskIndex = nextTask.getIndex();
    int lessonIndex = nextTask.getLesson().getIndex();
    Map<String, TaskFile> nextTaskFiles = nextTask.getTaskFiles();
    if (nextTaskFiles.isEmpty()) {
      return;
    }
    VirtualFile projectDir = project.getBaseDir();
    String lessonDirName = Lesson.LESSON_DIR + String.valueOf(lessonIndex + 1);
    if (projectDir == null) {
      return;
    }
    VirtualFile lessonDir = projectDir.findChild(lessonDirName);
    if (lessonDir == null) {
      return;
    }
    String taskDirName = Task.TASK_DIR + String.valueOf(nextTaskIndex + 1);
    VirtualFile taskDir = lessonDir.findChild(taskDirName);
    if (taskDir == null) {
      return;
    }
    VirtualFile shouldBeActive = null;
    for (Map.Entry<String, TaskFile> entry : nextTaskFiles.entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile vf = taskDir.findChild(name);
      if (vf != null) {
        FileEditorManager.getInstance(project).openFile(vf, true);
        if (!taskFile.getTaskWindows().isEmpty()) {
          shouldBeActive = vf;
        }
      }
    }
    if (shouldBeActive != null) {
      FileEditorManager.getInstance(project).openFile(shouldBeActive, true);
    }
  }

  protected abstract JButton getButton(StudyEditor selectedStudyEditor);

  @Override
  public void actionPerformed(AnActionEvent e) {
    navigateTask(e.getProject());
  }

  protected abstract String getNavigationFinishedMessage();

  protected abstract Task getTargetTask(Task sourceTask);
}
