package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PyStudyToolWindowConfigurator implements StudyToolWindowConfigurator {
  
  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StudyCheckAction());
    group.add(new StudyPreviousStudyTaskAction());
    group.add(new StudyNextStudyTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new StudyShowHintAction());

    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());
    return group;
  }

  @NotNull
  @Override
  public HashMap<String, JPanel> getAdditionalPanels(Project project) { 
    return new HashMap<>();
  }

  @NotNull
  @Override
  public FileEditorManagerListener getFileEditorManagerListener(@NotNull Project project,
                                                                @NotNull StudyToolWindow studyToolWindow) {
    return new FileEditorManagerListener() {

      private static final String EMPTY_TASK_TEXT = "Please, open any task to see task description";

      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Task task = getTask(file);
        setTaskText(task, file.getParent());
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        studyToolWindow.setTaskText(EMPTY_TASK_TEXT);
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file != null) {
          Task task = getTask(file);
          setTaskText(task, file.getParent());
        }
      }

      @Nullable
      private Task getTask(@NotNull VirtualFile file) {
        TaskFile taskFile = StudyUtils.getTaskFile(project, file);
        if (taskFile != null) {
          return taskFile.getTask();
        }
        else {
          return null;
        }
      }

      private void setTaskText(@Nullable final Task task, @Nullable final VirtualFile taskDirectory) {
        String text = StudyUtils.getTaskTextFromTask(task, taskDirectory);
        if (text == null) {
          studyToolWindow.setTaskText(EMPTY_TASK_TEXT);
          return;
        }
        studyToolWindow.setTaskText(text);
      }
    };
  }

  @NotNull
  @Override
  public String getDefaultHighlightingMode() {
    return "python";
  }

  @Override
  public boolean accept(@NotNull Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    return course != null && course.getLanguage().equals("Python") && course.getCourseType().equals("PyCharm");
  }
}
