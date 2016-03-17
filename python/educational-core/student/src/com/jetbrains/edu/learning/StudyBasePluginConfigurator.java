package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.twitter.StudyTwitterUtils;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

public abstract class StudyBasePluginConfigurator implements StudyPluginConfigurator {
  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    return getDefaultActionGroup();
  }

  @NotNull
  public static DefaultActionGroup getDefaultActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
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
  public Map<String, JPanel> getAdditionalPanels(Project project) {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public FileEditorManagerListener getFileEditorManagerListener(@NotNull Project project, @NotNull StudyToolWindow toolWindow) {

    return new FileEditorManagerListener() {

      private static final String EMPTY_TASK_TEXT = "Please, open any task to see task description";

      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Task task = getTask(file);
        setTaskText(task, file.getParent());
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        toolWindow.setTaskText(EMPTY_TASK_TEXT);
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
          toolWindow.setTaskText(EMPTY_TASK_TEXT);
          return;
        }
        toolWindow.setTaskText(text);
      }
    };
  }

  @Nullable
  @Override
  public StudyAfterCheckAction[] getAfterCheckActions() {
    return null;
  }

  @NotNull
  @Override
  public String getConsumerKey(@NotNull Project project) {
    return "";
  }

  @NotNull
  @Override
  public String getConsumerSecret(@NotNull Project project) {
    return "";
  }

  @Override
  public void storeTwitterTokens(@NotNull Project project, @NotNull String accessToken, @NotNull String tokenSecret) {
    // do nothing
  }
  
  @NotNull
  @Override
  public String getTwitterTokenSecret(@NotNull Project project) {
    return "";
  }

  @NotNull
  @Override
  public String getTwitterAccessToken(@NotNull Project project) {
    return "";
  }

  @Override
  public boolean askToTweet(@NotNull Project project, Task solvedTask, StudyStatus statusBeforeCheck) {
    return false;
  }

  @Nullable
  @Override
  public StudyTwitterUtils.TwitterDialogPanel getTweetDialogPanel(@NotNull Task solvedTask) {
    return null;
  }
}
