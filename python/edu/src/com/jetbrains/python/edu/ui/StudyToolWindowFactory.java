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
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.Lesson;
import com.jetbrains.python.edu.course.LessonInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * author: liana
 * data: 7/25/14.
 */

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "StudyToolWindow";
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

      int taskNum = 0;
      int taskSolved = 0;
      //int taskFailed = 0;
      for (Lesson lesson : course.getLessons()) {
        LessonInfo lessonInfo = lesson.getLessonInfo();
        taskNum += lessonInfo.getTaskNum();
        //taskFailed += lessonInfo.getTaskFailed();
        taskSolved += lessonInfo.getTaskSolved();
      }
      double percent = (taskSolved * 100.0) / taskNum;
      String statistics = UIUtil.toHtml(Math.floor(percent) + "% of course is passed", 5);
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      JLabel statisticLabel = new JLabel(statistics);
      contentPanel.add(statisticLabel);
      contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
      StudyProgressBar studyProgressBar = new StudyProgressBar(percent / 100, JBColor.GREEN, 40, 10);
      contentPanel.add(studyProgressBar);

      ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      Content content = contentFactory.createContent(contentPanel, "", true);
      toolWindow.getContentManager().addContent(content);
    }
  }
}
