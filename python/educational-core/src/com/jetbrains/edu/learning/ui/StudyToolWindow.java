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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.JBUI;
import com.jetbrains.edu.coursecreator.actions.CCEditTaskTextAction;
import com.jetbrains.edu.coursecreator.settings.CCSettings;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyFileEditorManagerListener;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.stepic.StepicAdaptiveReactionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public abstract class StudyToolWindow extends SimpleToolWindowPanel implements DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(StudyToolWindow.class);
  private static final String TASK_INFO_ID = "taskInfo";
  public static final String EMPTY_TASK_TEXT = "Please, open any task to see task description";
  private static final String HELP_ID = "task.description";

  private final JBCardLayout myCardLayout;
  private final JPanel myContentPanel;
  private final OnePixelSplitter mySplitPane;
  private JLabel myStatisticLabel;
  private StudyProgressBar myStudyProgressBar;
  private EditorEx myEditor;

  public StudyToolWindow() {
    super(true, true);
    myCardLayout = new JBCardLayout();
    myContentPanel = new JPanel(myCardLayout);
    mySplitPane = new OnePixelSplitter(myVertical = true);
  }

  public void init(@NotNull final Project project, final boolean isToolwindow) {
    final DefaultActionGroup group = getActionGroup(project);
    setActionToolbar(group);

    final JPanel panel = new JPanel(new BorderLayout());
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (isToolwindow && course != null && course.isAdaptive()) {
      panel.add(new StepicAdaptiveReactionsPanel(project), BorderLayout.NORTH);
    }

    JComponent taskInfoPanel = createTaskInfoPanel(project);
    panel.add(taskInfoPanel, BorderLayout.CENTER);

    final JPanel courseProgress = createCourseProgress(project);
    if (isToolwindow && course != null && !course.isAdaptive() && EduNames.STUDY.equals(course.getCourseMode())) {
      panel.add(courseProgress, BorderLayout.SOUTH);
    }

    myContentPanel.add(TASK_INFO_ID, panel);
    mySplitPane.setFirstComponent(myContentPanel);
    myCardLayout.show(myContentPanel, TASK_INFO_ID);

    setContent(mySplitPane);

    if (isToolwindow) {
      project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new StudyFileEditorManagerListener(this, project));
      Task task = StudyUtils.getCurrentTask(project);
      if (task == null) {
        setEmptyText(project);
        return;
      }
      String taskDescription = task.getTaskDescription();
      if (taskDescription == null) {
        setEmptyText(project);
        return;
      }
      setTaskText(taskDescription, project);
    }
  }

  public void setTopComponent(@NotNull final JComponent component) {
    mySplitPane.setFirstComponent(component);
  }

  public void setDefaultTopComponent() {
    mySplitPane.setFirstComponent(myContentPanel);
  }

  public void setActionToolbar(DefaultActionGroup group) {
    JPanel toolbarPanel = createToolbarPanel(group);
    setToolbar(toolbarPanel);
  }

  public void dispose() {
  }

  //used in checkiO plugin.
  @SuppressWarnings("unused")
  public void showPanelById(@NotNull final String panelId) {
    myCardLayout.swipe(myContentPanel, panelId, JBCardLayout.SwipeDirection.AUTO);
  }

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
  public void setTopComponentPreferredSize(@NotNull final Dimension dimension) {
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
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    if (configurator != null) {
      group.addAll(configurator.getTaskDescriptionActionGroup());
    }
    group.add(new CCEditTaskTextAction());
    return group;
  }

  public void setTaskText(@NotNull String text, @NotNull Project project) {
    if (StudyTaskManager.getInstance(project).isTurnEditingMode()) {
      enterEditingMode(project);
      StudyTaskManager.getInstance(project).setTurnEditingMode(false);
    }
    if (StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindowMode.EDITING) {
      enterEditingMode(project);
    }
    else {
      setText(text);
    }
  }

  public abstract void setText(@NotNull String text);
  
  public void updateFonts(@NotNull Project project) {
    
  }

  public void setEmptyText(@NotNull Project project) {
    if (StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindowMode.EDITING) {
      mySplitPane.setFirstComponent(myContentPanel);
      StudyTaskManager.getInstance(project).setTurnEditingMode(true);
    }
    setTaskText(EMPTY_TASK_TEXT, project);
  }

  public enum StudyToolWindowMode {
    TEXT, EDITING
  }


  public void enterEditingMode(Project project) {
    Task task = StudyUtils.getCurrentTask(project);
    assert task != null;
    String taskDescription = task.getTaskDescription(false);
    final EditorFactory factory = EditorFactory.getInstance();
    Document document = factory.createDocument(taskDescription);
    WebBrowserManager.getInstance().setShowBrowserHover(false);
    String extension = CCSettings.getInstance().useHtmlAsDefaultTaskFormat() ? "html" : "md";
    myEditor = (EditorEx)factory.createEditor(document, project, FileTypeRegistry.getInstance().getFileTypeByExtension(extension), false);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        if (!myEditor.isDisposed()) {
          factory.releaseEditor(myEditor);
        }
      }
    });
    JComponent editorComponent = myEditor.getComponent();
    editorComponent.setBorder(new EmptyBorder(10, 20, 0, 10));
    editorComponent.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    EditorSettings editorSettings = myEditor.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    mySplitPane.setFirstComponent(editorComponent);
    mySplitPane.repaint();

    StudyTaskManager.getInstance(project).setToolWindowMode(StudyToolWindowMode.EDITING);
  }


  public void leaveEditingMode(Project project) {
    Task task = StudyUtils.getCurrentTask(project);
    assert task != null;
    task.saveTaskText(myEditor.getDocument().getText());
    EditorFactory.getInstance().releaseEditor(myEditor);
    WebBrowserManager.getInstance().setShowBrowserHover(true);
    mySplitPane.setFirstComponent(myContentPanel);
    StudyTaskManager.getInstance(project).setToolWindowMode(StudyToolWindowMode.TEXT);
    StudyUtils.updateToolWindows(project);
  }

  private JPanel createCourseProgress(@NotNull final Project project) {
    JPanel contentPanel = new JPanel();
    contentPanel.setBackground(JBColor.WHITE);
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
    contentPanel.add(Box.createRigidArea(new Dimension(10, 0)));
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    myStudyProgressBar = new StudyProgressBar(0, 20, 10);

    myStatisticLabel = new JLabel("", SwingConstants.LEFT);
    contentPanel.add(myStatisticLabel);
    contentPanel.add(myStudyProgressBar);

    contentPanel.setPreferredSize(new Dimension(100, 60));
    contentPanel.setMinimumSize(new Dimension(300, 40));
    updateCourseProgress(project);
    return contentPanel;
  }

  public void updateCourseProgress(@NotNull final Project project) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null) {
      List<Lesson> lessons = course.getLessons();

      Pair<Integer, Integer> progress = countProgressAsOneTaskWithSubtasks(lessons);
      if (progress == null) {
        progress = countProgressWithoutSubtasks(lessons);
      }

      int taskSolved = progress.getFirst();
      int taskNum = progress.getSecond();
      String completedTasks = String.format("%d of %d tasks completed", taskSolved, taskNum);
      double percent = (taskSolved * 100.0) / taskNum;

      myStatisticLabel.setText(completedTasks);
      myStudyProgressBar.setFraction(percent / 100);
    }
  }

  /**
   * Counts current progress for course which consists of only on task with subtasks
   * In this case we count each subtasks as task
   * @return Pair (number of solved tasks, number of tasks) or null if lessons can't be interpreted as one task with subtasks
   */
  @Nullable
  private static Pair<Integer, Integer> countProgressAsOneTaskWithSubtasks(List<Lesson> lessons) {
    if (lessons.size() == 1 && lessons.get(0).getTaskList().size() == 1) {
      final Lesson lesson = lessons.get(0);
      final Task task = lesson.getTaskList().get(0);
      if (task instanceof TaskWithSubtasks) {
        final int lastSubtaskIndex = ((TaskWithSubtasks)task).getLastSubtaskIndex();
        final int activeSubtaskIndex = ((TaskWithSubtasks)task).getActiveSubtaskIndex();
        int taskNum = lastSubtaskIndex + 1;
        boolean isLastSubtaskSolved = activeSubtaskIndex == lastSubtaskIndex && task.getStatus() == StudyStatus.Solved;
        return Pair.create(isLastSubtaskSolved ? taskNum : activeSubtaskIndex, taskNum);
      }
    }
    return null;
  }

  /**
   * @return Pair (number of solved tasks, number of tasks)
   */
  @NotNull
  private static Pair<Integer, Integer> countProgressWithoutSubtasks(List<Lesson> lessons) {
    int taskNum = 0;
    int taskSolved = 0;
    for (Lesson lesson : lessons) {
      taskNum += lesson.getTaskList().size();
      taskSolved += getSolvedTasks(lesson);
    }
    return Pair.create(taskSolved, taskNum);
  }

  private static int getSolvedTasks(@NotNull final Lesson lesson) {
    int solved = 0;
    for (Task task : lesson.getTaskList()) {
      if (task.getStatus() == StudyStatus.Solved) {
        solved += 1;
      }
    }
    return solved;
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }
}
