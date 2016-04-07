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
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.StudyBasePluginConfigurator;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

public abstract class StudyToolWindow extends SimpleToolWindowPanel implements DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(StudyToolWindow.class);
  private static final String TASK_INFO_ID = "taskInfo";
  private final JBCardLayout myCardLayout;
  private final JPanel myContentPanel;
  private final OnePixelSplitter mySplitPane;

  public StudyToolWindow() {
    super(true, true);
    myCardLayout = new JBCardLayout();
    myContentPanel = new JPanel(myCardLayout);
    mySplitPane = new OnePixelSplitter(myVertical=true);
  }

  public void init(Project project) {
    String taskText = StudyUtils.getTaskText(project);
    if (taskText == null) return;

    JPanel toolbarPanel = createToolbarPanel(project);
    setToolbar(toolbarPanel);

    final JPanel panel = new JPanel(new VerticalFlowLayout());
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && course.isAdaptive()) {
      panel.add(createReactionPanel());
    }
    panel.add(createTaskInfoPanel(taskText, project));
    myContentPanel.add(TASK_INFO_ID, panel);
    mySplitPane.setFirstComponent(myContentPanel);
    addAdditionalPanels(project);
    myCardLayout.show(myContentPanel, TASK_INFO_ID);

    setContent(mySplitPane);

    StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
    if (configurator != null) {
      final FileEditorManagerListener listener = configurator.getFileEditorManagerListener(project, this);
      project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    }
  }

  private void addAdditionalPanels(Project project) {
    StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
    if (configurator != null) {
      Map<String, JPanel> panels = configurator.getAdditionalPanels(project);
      for (Map.Entry<String, JPanel> entry : panels.entrySet()) {
        myContentPanel.add(entry.getKey(), entry.getValue());
      }
    }
  }
  
  public JPanel createReactionPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()));
    final JPanel hardPanel = new JPanel(new BorderLayout());
    hardPanel.add(new JLabel("Too hard"), BorderLayout.CENTER);
    hardPanel.setBorder(BorderFactory.createEtchedBorder());

    final JPanel boringPanel = new JPanel(new BorderLayout());
    boringPanel.setBorder(BorderFactory.createEtchedBorder());
    boringPanel.add(new JLabel("Too boring"), BorderLayout.CENTER);

    final GridBagConstraints c = new GridBagConstraints();
    c.fill  = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    panel.add(hardPanel, c);
    c.gridx =  1;
    panel.add(boringPanel, c);
    c.gridy = 1;
    c.weightx = 1;
    panel.add(Box.createVerticalBox(), c);

    final Project project = ProjectUtil.guessCurrentProject(myContentPanel);
    addMouseListener(hardPanel, () -> EduAdaptiveStepicConnector.addNextRecommendedTask(project, 0));
    addMouseListener(boringPanel, () -> EduAdaptiveStepicConnector.addNextRecommendedTask(project, -1));
    
    return panel;
  }

  private static void addMouseListener(@NotNull final JPanel panel, @NotNull Runnable onClickAction ) {
    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1) {
          final ProgressIndicatorBase progress = new ProgressIndicatorBase();
          progress.setText("Loading Next Recommendation");
          ProgressManager.getInstance().run(new Task.Backgroundable(ProjectUtil.guessCurrentProject(panel),
                                                                    "Loading Next Recommendation") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              onClickAction.run();
            }
          });
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        panel.setBackground(UIUtil.getTreeSelectionBackground());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        panel.setBackground(UIUtil.getLabelBackground());
      }
    });
  }
  
  public void dispose() {
  }
  
  //used in checkiO plugin.
  @SuppressWarnings("unused")
  public void showPanelById(@NotNull final String panelId) {
    myCardLayout.swipe(myContentPanel, panelId, JBCardLayout.SwipeDirection.AUTO);
  }

  //used in checkiO plugin.
  @SuppressWarnings("unused")
  public void setBottomComponent(JComponent component) {
    mySplitPane.setSecondComponent(component);
  }

  //used in checkiO plugin.
  @SuppressWarnings("unused")
  public JComponent getBottomComponent() {
    return mySplitPane.getSecondComponent();
  }

  //used in checkiO plugin.
  @SuppressWarnings("unused")
  public void setTopComponentPrefferedSize(@NotNull final Dimension dimension) {
    myContentPanel.setPreferredSize(dimension);
  }

  //used in checkiO plugin.
  @SuppressWarnings("unused")
  public JPanel getContentPanel() {
    return myContentPanel;
  }


  public abstract JComponent createTaskInfoPanel(String taskText, Project project);

  private static JPanel createToolbarPanel(@NotNull final Project project) {
    final DefaultActionGroup group = getActionGroup(project);

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Study", group, true);
    return JBUI.Panels.simplePanel(actionToolBar.getComponent());
  }

  private static DefaultActionGroup getActionGroup(@NotNull final Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      LOG.warn("Course is null");
      return new DefaultActionGroup();
    }
    StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
    if (configurator != null) {
      return configurator.getActionGroup(project);
    }
    else {
      LOG.warn("No configurator is provided for plugin");
      return StudyBasePluginConfigurator.getDefaultActionGroup();
    }
  }

  public abstract void setTaskText(String text) ;
}
