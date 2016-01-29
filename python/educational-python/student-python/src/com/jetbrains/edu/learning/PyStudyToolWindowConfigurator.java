package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.learning.actions.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PyStudyToolWindowConfigurator implements StudyToolWindowConfigurator {
  
  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(StudyCheckAction.createCheckAction(course));
    group.add(new StudyPreviousStudyTaskAction());
    group.add(new StudyNextStudyTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new StudyShowHintAction());

    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());
    return group;
  }

  @Override
  public HashMap<String, JPanel> getAdditionalPanels(Project project) { 
    return new HashMap<>();
  }

  @Override
  public boolean accept(@NotNull Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    return course != null && course.getLanguage().equals("Python") && course.getCourseType().equals("PyCharm");
  }
}
