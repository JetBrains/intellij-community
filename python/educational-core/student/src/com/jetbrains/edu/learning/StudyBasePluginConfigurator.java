package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.editor.StudyChoiceVariantsPanel;
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
    group.add(new StudyPreviousTaskAction());
    group.add(new StudyNextTaskAction());
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
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Task task = getTask(file);
        setTaskText(task, StudyUtils.getTaskDir(file));
        if (task != null) {
          if (task.isChoiceTask()) {
            final StudyChoiceVariantsPanel choicePanel = new StudyChoiceVariantsPanel(task);
            toolWindow.setBottomComponent(choicePanel);
          }
          else {
            toolWindow.setBottomComponent(null);
          }
        }
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        for (VirtualFile openedFile : source.getOpenFiles()) {
          if (StudyUtils.getTaskFile(project, openedFile) != null) {
            return;
          }
        }
        toolWindow.setEmptyText(project);
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file != null) {
          Task task = getTask(file);
          setTaskText(task, StudyUtils.getTaskDir(file));
        }
        toolWindow.setBottomComponent(null);
      }

      @Nullable
      private Task getTask(@NotNull VirtualFile file) {
        return StudyUtils.getTaskForFile(project, file);
      }

      private void setTaskText(@Nullable final Task task, @Nullable final VirtualFile taskDirectory) {
        String text = StudyUtils.getTaskTextFromTask(taskDirectory, task);
        if (text == null) {
          toolWindow.setEmptyText(project);
          return;
        }
        toolWindow.setTaskText(text, taskDirectory, project);
      }
    };
  }

  @Nullable
  @Override
  public StudyAfterCheckAction[] getAfterCheckActions() {
    return null;
  }
}
