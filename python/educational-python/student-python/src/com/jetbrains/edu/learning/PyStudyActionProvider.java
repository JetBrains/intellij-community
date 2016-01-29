package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.learning.actions.*;
import org.jetbrains.annotations.NotNull;

public class PyStudyActionProvider implements StudyActionProvider {
  
  @Override
  public DefaultActionGroup getActionGroup(@NotNull Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && course.getLanguage().equals("Python") && course.getCourseType().equals("PyCharm")) {
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
    return null;
  }
}
