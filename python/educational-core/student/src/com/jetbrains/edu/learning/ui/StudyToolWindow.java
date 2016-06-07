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

import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.*;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
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
  private static final String EMPTY_TASK_TEXT = "Please, open any task to see task description";
  private static final String HARD_REACTION = "Too Hard";
  private static final String BORING_REACTION = "Too Boring";
  private static final String SOLVED_TASK_TOOLTIP = "Task Is Solved";
  private static final String HARD_LABEL_TOOLTIP = "Click To Get An Easier Task";
  private static final String BORING_LABEL_TOOLTIP = "Click To Get A More Challenging Task";
  
  private final JBCardLayout myCardLayout;
  private final JPanel myContentPanel;
  private final OnePixelSplitter mySplitPane;

  public StudyToolWindow() {
    super(true, true);
    myCardLayout = new JBCardLayout();
    myContentPanel = new JPanel(myCardLayout);
    mySplitPane = new OnePixelSplitter(myVertical = true);
  }

  public void init(Project project) {
    String taskText = StudyUtils.getTaskText(project);
    if (taskText == null) return;

    JPanel toolbarPanel = createToolbarPanel(getActionGroup(project));
    setToolbar(toolbarPanel);
    
    final JPanel panel = new JPanel(new BorderLayout());
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && course.isAdaptive()) {
      panel.add(createReactionPanel(), BorderLayout.NORTH);
    }
    JComponent taskInfoPanel = createTaskInfoPanel(project);
    panel.add(taskInfoPanel, BorderLayout.CENTER);
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

    if (StudyTaskManager.getInstance(project).isTurnEditingMode() || StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindowMode.EDITING) {
      TaskFile file = StudyUtils.getSelectedTaskFile(project);
      if (file != null) {
        VirtualFile taskDir = file.getTask().getTaskDir(project);
        setTaskText(taskText, taskDir, project);

      }
    } else {
      setTaskText(taskText, null, project);
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
    final JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setBackground(UIUtil.getTextFieldBackground());

    final JPanel hardPanel = createReactionButtonPanel(HARD_REACTION, HARD_LABEL_TOOLTIP, SOLVED_TASK_TOOLTIP, 0);
    final JPanel boringPanel = createReactionButtonPanel(BORING_REACTION, BORING_LABEL_TOOLTIP, SOLVED_TASK_TOOLTIP, -1);
    
    final GridBagConstraints c = new GridBagConstraints();
    c.fill  = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    mainPanel.add(Box.createVerticalStrut(3), c);
    
    c.gridx = 1;
    c.gridy = 1;
    mainPanel.add(Box.createHorizontalStrut(3), c);
    c.weightx = 1;
    c.gridx = 2;
    mainPanel.add(hardPanel, c);
    c.gridx = 3;
    c.weightx = 0;
    mainPanel.add(Box.createHorizontalStrut(3), c);
    c.weightx = 1;
    c.gridx = 4;
    mainPanel.add(boringPanel, c);
    c.gridx = 5;
    c.weightx = 0;
    mainPanel.add(Box.createHorizontalStrut(3), c);
    
    return mainPanel;
  }

  private JPanel createReactionButtonPanel(@NotNull final String text,
                                           @NotNull final String enabledTooltip,
                                           @NotNull final String disabledTooltip,
                                           int reaction) {
    final Project project = ProjectUtil.guessCurrentProject(myContentPanel);
    final com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getCurrentTask(project);
    final boolean isEnabled = task != null && task.getStatus() != StudyStatus.Solved;
    
    final JLabel label = new JLabel(text);
    label.setEnabled(isEnabled);

    final JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.PAGE_AXIS));
    buttonPanel.setToolTipText(isEnabled ? enabledTooltip : disabledTooltip);
    buttonPanel.add(Box.createVerticalStrut(5));
    buttonPanel.add(label);
    buttonPanel.add(Box.createVerticalStrut(5));

    final JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setBorder(BorderFactory.createEtchedBorder());
    mainPanel.add(buttonPanel);
    addMouseListener(mainPanel, buttonPanel, label, () -> EduAdaptiveStepicConnector.addNextRecommendedTask(project, reaction));
    return mainPanel;
  }

  private static void addMouseListener(@NotNull final JPanel panel,
                                       @NotNull final JPanel buttonPanel,
                                       @NotNull final JLabel textLabel,
                                       @NotNull Runnable onClickAction) {
    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1) {
          final com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getCurrentTask(ProjectUtil.guessCurrentProject(panel));
          if (task != null && task.getStatus() != StudyStatus.Solved) {
            final ProgressIndicatorBase progress = new ProgressIndicatorBase();
            progress.setText("Loading Next Recommendation");
            ProgressManager.getInstance().run(new Task.Backgroundable(ProjectUtil.guessCurrentProject(panel),
                                                                      "Loading Next Recommendation") {

              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                setEnabled(false);
                onClickAction.run();
              }

              @Override
              protected void onFinished() {
                setEnabled(true);
              }

              private void setEnabled(final boolean enabled) {
                ApplicationManager.getApplication().invokeLater(() -> {
                  panel.setEnabled(enabled);
                  buttonPanel.setEnabled(enabled);
                  textLabel.setEnabled(enabled);
                });
              }
            });
          }
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        final com.jetbrains.edu.learning.courseFormat.Task task = StudyUtils.getCurrentTask(ProjectUtil.guessCurrentProject(panel));
        if (task != null && task.getStatus() != StudyStatus.Solved && panel.isEnabled()) {
          setBackground(UIUtil.getButtonSelectColor());
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setBackground(UIUtil.getLabelBackground());
      }

      private void setBackground(Color color) {
        panel.setBackground(color);
        buttonPanel.setBackground(color);
      }
    };
    panel.addMouseListener(mouseAdapter);
    buttonPanel.addMouseListener(mouseAdapter);
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


  public abstract JComponent createTaskInfoPanel(Project project);

  public static JPanel createToolbarPanel(ActionGroup group) {
    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Study", group, true);
    return JBUI.Panels.simplePanel(actionToolBar.getComponent());
  }

  public static DefaultActionGroup getActionGroup(@NotNull final Project project) {
    DefaultActionGroup group = new DefaultActionGroup();
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      LOG.warn("Course is null");
      return group;
    }
    StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
    if (configurator != null) {
      group.addAll(configurator.getActionGroup(project));
      addAdditionalActions(group);
      return group;
    }
    else {
      LOG.warn("No configurator is provided for plugin");
      return StudyBasePluginConfigurator.getDefaultActionGroup();
    }
  }

  private static void addAdditionalActions(DefaultActionGroup group) {
    StudyActionsProvider[] providers = Extensions.getExtensions(StudyActionsProvider.EP_NAME);
    for (StudyActionsProvider provider : providers) {
      group.addAll(provider.getActions());
    }
  }

  public void setTaskText(String text, VirtualFile taskDirectory, Project project) {
    if (StudyTaskManager.getInstance(project).isTurnEditingMode()) {
      if (taskDirectory == null) {
        LOG.info("Failed to enter editing mode for StudyToolWindow");
        return;
      }
      VirtualFile taskTextFile = taskDirectory.findChild(EduNames.TASK_HTML);
      enterEditingMode(taskTextFile, project);
      StudyTaskManager.getInstance(project).setTurnEditingMode(false);
    }
    else {
      setText(text);
    }
  }

  protected abstract void setText(@NotNull String text);

  public void setEmptyText(@NotNull Project project) {
    if (StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindowMode.EDITING) {
      mySplitPane.setFirstComponent(myContentPanel);
      StudyTaskManager.getInstance(project).setTurnEditingMode(true);
    }
    setTaskText(EMPTY_TASK_TEXT, null, project);
  }

  public enum StudyToolWindowMode {
    TEXT, EDITING
  }


  public void enterEditingMode(VirtualFile taskFile, Project project) {
    final EditorFactory factory = EditorFactory.getInstance();
    Document document = FileDocumentManager.getInstance().getDocument(taskFile);
    if (document == null) {
      return;
    }
    WebBrowserManager.getInstance().setShowBrowserHover(false);
    final EditorEx createdEditor = (EditorEx)factory.createEditor(document, project, taskFile, false);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        factory.releaseEditor(createdEditor);
      }
    });
    JComponent editorComponent = createdEditor.getComponent();
    mySplitPane.setFirstComponent(editorComponent);
    mySplitPane.repaint();

    StudyTaskManager.getInstance(project).setToolWindowMode(StudyToolWindowMode.EDITING);
  }


  public void leaveEditingMode(Project project) {
    WebBrowserManager.getInstance().setShowBrowserHover(true);
    mySplitPane.setFirstComponent(myContentPanel);
    StudyTaskManager.getInstance(project).setToolWindowMode(StudyToolWindowMode.TEXT);
    StudyUtils.updateStudyToolWindow(project);
  }
}
