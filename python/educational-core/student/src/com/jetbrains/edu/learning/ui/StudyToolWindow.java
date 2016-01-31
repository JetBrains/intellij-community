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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyBundle;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyToolWindowConfigurator;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class StudyToolWindow extends SimpleToolWindowPanel implements DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(StudyToolWindow.class);
  private static final String EMPTY_TASK_TEXT = StudyBundle.message("empty.task.text");
  private static final String TASK_INFO_ID = "taskInfo";
  private final JBCardLayout myCardLayout;
  private final JPanel myContentPanel;
  private final OnePixelSplitter mySplitPane;
  private StudyBrowserWindow myBrowserWindow;

  public StudyToolWindow(final Project project) {
    super(true, true);
    myCardLayout = new JBCardLayout();
    myContentPanel = new JPanel(myCardLayout);
    mySplitPane = new OnePixelSplitter(myVertical=true);
    
    String taskText = getTaskText(project);
    if (taskText == null) return;
    
    JPanel toolbarPanel = createToolbarPanel(project);
    setToolbar(toolbarPanel);

    myContentPanel.add(TASK_INFO_ID, createTaskInfoPanel(taskText));
    mySplitPane.setFirstComponent(myContentPanel);
    addAdditionalPanels(project);
    myCardLayout.show(myContentPanel, TASK_INFO_ID);
    
    setContent(mySplitPane);

    StudyToolWindowConfigurator configurator = getStudyToolWindowConfigurator(project);
    assert configurator != null;
    final FileEditorManagerListener listener = configurator.getFileEditorManagerListener(project, this);
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
  }

  private void addAdditionalPanels(Project project) {
    StudyToolWindowConfigurator configurator = getStudyToolWindowConfigurator(project);
    assert configurator != null;
    HashMap<String, JPanel> panels = configurator.getAdditionalPanels(project);
    for (Map.Entry<String, JPanel> entry: panels.entrySet()) {
      myContentPanel.add(entry.getKey(), entry.getValue());
    }
  }

  public void dispose() {
  }
  
  public void showPanelById(@NotNull final String panelId) {
    myCardLayout.swipe(myContentPanel, panelId, JBCardLayout.SwipeDirection.AUTO);
  }
  
  public void setButtomComponent(JComponent component) {
    mySplitPane.setSecondComponent(component);
  }
  
  private static String getTaskText(@NotNull final Project project) {
    VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
    TaskFile taskFile = null;
    for (VirtualFile file : files) {
      taskFile = StudyUtils.getTaskFile(project, file);
      if (taskFile != null) {
        break;
      }
    }
    if (taskFile == null) {
      return EMPTY_TASK_TEXT;
    }
    final Task task = taskFile.getTask();
    if (task != null) {
      return StudyUtils.getTaskTextFromTask(task, task.getTaskDir(project));
    }
    return null;
  }
  
  private JPanel createTaskInfoPanel(String taskText) {
    myBrowserWindow = new StudyBrowserWindow();
    myBrowserWindow.setShowProgress(false);
    myBrowserWindow.openLinkInNewWindow(true);
    myBrowserWindow.addBackAndOpenButtons();
    myBrowserWindow.loadContent(taskText);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    panel.add(myBrowserWindow.getPanel());
    return panel;
  }

  private static JPanel createToolbarPanel(@NotNull final Project project) {
    final DefaultActionGroup group = getActionGroup(project);

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Study", group, true);
    return JBUI.Panels.simplePanel(actionToolBar.getComponent());
  }

  private static DefaultActionGroup getActionGroup(@NotNull final Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return createDefaultActionGroup(project);
    }
    StudyToolWindowConfigurator configurator = getStudyToolWindowConfigurator(project);
    assert configurator != null;

    return configurator.getActionGroup(project);
  }

  @NotNull
  private static DefaultActionGroup createDefaultActionGroup(@NotNull Project project) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(StudyCheckAction.createCheckAction(StudyTaskManager.getInstance(project).getCourse()));
    group.add(new StudyPreviousStudyTaskAction());
    group.add(new StudyNextStudyTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new StudyShowHintAction());
    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());
    return group;
  }

  public void setTaskText(String text) {
    myBrowserWindow.loadContent(text);
  }

  @Nullable
  private static StudyToolWindowConfigurator getStudyToolWindowConfigurator(@NotNull Project project) {
    StudyToolWindowConfigurator[] extensions = StudyToolWindowConfigurator.EP_NAME.getExtensions();
    for (StudyToolWindowConfigurator extension: extensions) {
      if (extension.accept(project)) {
        return extension;
      }
    }
    return null;
  }
}
