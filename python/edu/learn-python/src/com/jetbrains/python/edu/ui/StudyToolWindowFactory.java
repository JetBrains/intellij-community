package com.jetbrains.python.edu.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.actions.StudyReloadCourseAction;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.Lesson;
import com.jetbrains.python.edu.course.LessonInfo;
import com.jetbrains.python.edu.course.StudyStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "Course Description";
  JPanel contentPanel = new JPanel();

  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    if (StudyTaskManager.getInstance(project).getCourse() != null) {
      contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
      contentPanel.add(Box.createRigidArea(new Dimension(10, 0)));
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      Course course = taskManager.getCourse();
      if (course == null) {
        return;
      }
      String courseName = UIUtil.toHtml("<h1>" + course.getName() + "</h1>", 10);
      String description = UIUtil.toHtml(course.getDescription(), 5);
      String author = taskManager.getCourse().getAuthor();
      String authorLabel = UIUtil.toHtml("<b>Author: </b>" + author, 5);
      contentPanel.add(new JLabel(courseName));
      contentPanel.add(new JLabel(authorLabel));
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      contentPanel.add(new JLabel(description));
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      JButton reloadCourseButton = new JButton("reload course");
      reloadCourseButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          StudyReloadCourseAction.reloadCourse(project);
        }
      });

      contentPanel.add(reloadCourseButton);
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
      contentPanel.add(Box.createVerticalStrut(10));
      addStatistics(completedLessons);
      addStatistics(completedTasks);

      double percent = (taskSolved * 100.0) / taskNum;
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      StudyProgressBar studyProgressBar = new StudyProgressBar(percent / 100, JBColor.GREEN, 40, 10);
      contentPanel.add(studyProgressBar);
      addStatistics(tasksLeft);
      ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      Content content = contentFactory.createContent(contentPanel, "", true);
      toolWindow.getContentManager().addContent(content);
    }
  }

  private void addStatistics(String statistics) {
    String labelText = UIUtil.toHtml(statistics, 5);
    contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    JLabel statisticLabel = new JLabel(labelText);
    contentPanel.add(statisticLabel);
  }
}
