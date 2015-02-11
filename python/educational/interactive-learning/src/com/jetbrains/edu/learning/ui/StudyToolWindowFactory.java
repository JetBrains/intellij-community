package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.course.Course;
import com.jetbrains.edu.learning.course.Lesson;
import com.jetbrains.edu.learning.course.LessonInfo;
import com.jetbrains.edu.learning.course.StudyStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "Course Description";
  private JPanel myContentPanel = new JPanel();

  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    if (StudyTaskManager.getInstance(project).getCourse() != null) {
      myContentPanel.setLayout(new BoxLayout(myContentPanel, BoxLayout.PAGE_AXIS));
      myContentPanel.add(Box.createRigidArea(new Dimension(10, 0)));
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      Course course = taskManager.getCourse();
      if (course == null) {
        return;
      }
      String courseName = UIUtil.toHtml("<h1>" + course.getName() + "</h1>", 10);
      String description = UIUtil.toHtml(course.getDescription(), 5);
      String author = taskManager.getCourse().getAuthor();
      String authorLabel = UIUtil.toHtml("<b>Author: </b>" + author, 5);
      myContentPanel.add(new JLabel(courseName));
      myContentPanel.add(new JLabel(authorLabel));
      myContentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      myContentPanel.add(new JLabel(description));
      myContentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      int taskNum = 0;
      int taskSolved = 0;
      int lessonsCompleted = 0;
      List<Lesson> lessons = course.getLessons();
      for (Lesson lesson : lessons) {
        if (lesson.getStatus() == StudyStatus.Solved) {
          lessonsCompleted++;
        }
        LessonInfo lessonInfo = lesson.getLessonInfo();
        taskNum += lessonInfo.getTaskNum();
        taskSolved += lessonInfo.getTaskSolved();
      }
      String completedLessons = String.format("%d of %d lessons completed", lessonsCompleted, course.getLessons().size());
      String completedTasks = String.format("%d of %d tasks completed", taskSolved, taskNum);
      String tasksLeft = String.format("%d of %d tasks left", taskNum - taskSolved, taskNum);
      myContentPanel.add(Box.createVerticalStrut(10));
      addStatistics(completedLessons);
      addStatistics(completedTasks);

      double percent = (taskSolved * 100.0) / taskNum;
      myContentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      StudyProgressBar studyProgressBar = new StudyProgressBar(percent / 100, 40, 10);
      myContentPanel.add(studyProgressBar);
      addStatistics(tasksLeft);
      ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      Content content = contentFactory.createContent(myContentPanel, "", true);
      toolWindow.getContentManager().addContent(content);
    }
  }

  private void addStatistics(String statistics) {
    String labelText = UIUtil.toHtml(statistics, 5);
    myContentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    JLabel statisticLabel = new JLabel(labelText);
    myContentPanel.add(statisticLabel);
  }
}
