package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "Course Description";


  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    JPanel contentPanel = new JPanel();
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (taskManager.getCourse() != null) {
      contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
      contentPanel.add(Box.createRigidArea(new Dimension(10, 0)));

      Course course = taskManager.getCourse();
      if (course == null) {
        return;
      }
      String courseName = UIUtil.toHtml("<h1>" + course.getName() + "</h1>", 10);
      String description = UIUtil.toHtml(course.getDescription(), 5);
      String authorLabel = UIUtil.toHtml("<b>Author: </b>" + Course.getAuthorsString(course.getAuthors()), 5);
      contentPanel.add(new JLabel(courseName));
      contentPanel.add(new JLabel(authorLabel));
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      contentPanel.add(new JLabel(description));
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      int taskNum = 0;
      int taskSolved = 0;
      int lessonsCompleted = 0;
      List<Lesson> lessons = course.getLessons();
      for (Lesson lesson : lessons) {
        StudyStatus status = taskManager.getStatus(lesson);
        if (status == StudyStatus.Solved) {
          lessonsCompleted++;
        }
        taskNum += lesson.getTaskList().size();
        taskSolved += getSolvedTasks(lesson, taskManager);
      }
      String completedLessons = String.format("%d of %d lessons completed", lessonsCompleted, course.getLessons().size());
      String completedTasks = String.format("%d of %d tasks completed", taskSolved, taskNum);
      String tasksLeft = String.format("%d of %d tasks left", taskNum - taskSolved, taskNum);
      contentPanel.add(Box.createVerticalStrut(10));
      addStatistics(completedLessons, contentPanel);
      addStatistics(completedTasks, contentPanel);

      double percent = (taskSolved * 100.0) / taskNum;
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      StudyProgressBar studyProgressBar = new StudyProgressBar(percent / 100, 40, 10);
      contentPanel.add(studyProgressBar);
      addStatistics(tasksLeft, contentPanel);
      ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      Content content = contentFactory.createContent(contentPanel, "", true);
      toolWindow.getContentManager().addContent(content);
    }
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

  private static void addStatistics(String statistics, JPanel contentPanel) {
    String labelText = UIUtil.toHtml(statistics, 5);
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    JLabel statisticLabel = new JLabel(labelText);
    contentPanel.add(statisticLabel);
  }
}
