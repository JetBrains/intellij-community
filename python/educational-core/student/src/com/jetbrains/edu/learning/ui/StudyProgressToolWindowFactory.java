package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.StudyTaskManager;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StudyProgressToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String ID = "Course Progress";


  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    toolWindow.setIcon(InteractiveLearningIcons.CourseProgress);
    JPanel contentPanel = new JPanel();
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (taskManager.getCourse() != null) {
      contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
      contentPanel.add(Box.createRigidArea(new Dimension(10, 0)));

      Course course = taskManager.getCourse();
      if (course == null) {
        return;
      }
      int taskNum = 0;
      int taskSolved = 0;
      List<Lesson> lessons = course.getLessons();
      for (Lesson lesson : lessons) {
        if (lesson.getName().equals(EduNames.PYCHARM_ADDITIONAL)) continue;
        taskNum += lesson.getTaskList().size();
        taskSolved += getSolvedTasks(lesson, taskManager);
      }
      String completedTasks = String.format("%d of %d tasks completed", taskSolved, taskNum);

      double percent = (taskSolved * 100.0) / taskNum;
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      StudyProgressBar studyProgressBar = new StudyProgressBar(percent / 100, 40, 10);
      contentPanel.add(studyProgressBar);
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      addStatistics(completedTasks, contentPanel);
      contentPanel.setPreferredSize(new Dimension(100, 50));
      ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      Content content = contentFactory.createContent(contentPanel, "", false);
      contentPanel.setMinimumSize(new Dimension(300, 100));
      toolWindow.getContentManager().addContent(content);
    }
  }

  private static void addStatistics(String statistics, JPanel contentPanel) {
    String labelText = UIUtil.toHtml(statistics, 5);
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    JLabel statisticLabel = new JLabel(labelText);
    contentPanel.add(statisticLabel);
  }

  private static int getSolvedTasks(@NotNull final Lesson lesson, StudyTaskManager taskManager) {
    int solved = 0;
    for (Task task : lesson.getTaskList()) {
      if (taskManager.getStatus(task) == StudyStatus.Solved) {
        solved += 1;
      }
    }
    return solved;
  }
}
