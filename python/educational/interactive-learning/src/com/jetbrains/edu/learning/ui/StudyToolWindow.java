/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class StudyToolWindow extends SimpleToolWindowPanel implements DataProvider, Disposable {

  public StudyToolWindow(final Project project) {
    super(true, true);
    JPanel toolbarPanel = createToolbarPanel();
    setToolbar(toolbarPanel);

    final StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    if (studyEditor == null) return;
    Task task = studyEditor.getTaskFile().getTask();

    if (task != null) {
      final String taskText = task.getText();

      final JTextPane taskTextPane = new JTextPane();
      JBScrollPane scrollPane = new JBScrollPane(taskTextPane);
      taskTextPane.setContentType("text/html");
      taskTextPane.setEditable(false);
      taskTextPane.setText(taskText);
      taskTextPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
      taskTextPane.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
      taskTextPane.setBorder(new EmptyBorder(15, 20, 0, 100));
      setContent(scrollPane);

      final FileEditorManagerListener listener = new StudyFileEditorManagerListener(project, taskTextPane);
      project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    }

  }

  public void dispose() {
  }

  private static JPanel createToolbarPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StudyCheckAction());
    group.add(new StudyPreviousStudyTaskAction());
    group.add(new StudyNextStudyTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new StudyShowHintAction());

    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Study", group, true);
    return JBUI.Panels.simplePanel(actionToolBar.getComponent());
  }

  static class StudyFileEditorManagerListener implements FileEditorManagerListener {
    private Project myProject;
    private JTextPane myTaskTextPane;

    StudyFileEditorManagerListener(@NotNull final Project project, JTextPane taskTextPane){
      myProject = project;
      myTaskTextPane = taskTextPane;
    }
      @Override
      public void fileOpened (@NotNull FileEditorManager source, @NotNull VirtualFile file){
      Task task = getTask(file);
      setTaskText(task);
    }

      @Override
      public void fileClosed (@NotNull FileEditorManager source, @NotNull VirtualFile file){
    }

      @Override
      public void selectionChanged (@NotNull FileEditorManagerEvent event){
      VirtualFile file = event.getNewFile();
      if (file != null) {
        Task task = getTask(file);
        setTaskText(task);
      }
    }

      @Nullable
      private Task getTask (@NotNull VirtualFile file){
      TaskFile taskFile = StudyUtils.getTaskFile(myProject, file);
      if (taskFile != null) {
        return taskFile.getTask();
      }
      else {
        return null;
      }
    }

    private void setTaskText(@Nullable final Task task) {
      if (task == null) {
        return;
      }
      String text = task.getText();
      myTaskTextPane.setText(text);
    }
  }
}
