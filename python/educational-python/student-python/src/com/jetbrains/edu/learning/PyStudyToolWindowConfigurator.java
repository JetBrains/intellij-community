package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

public class PyStudyToolWindowConfigurator extends StudyBaseToolWindowConfigurator {
  
  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    final DefaultActionGroup group = super.getActionGroup(project);
    group.add(new PyStudyCheckAction());
    return group;
  }

  @NotNull
  @Override
  public String getDefaultHighlightingMode() {
    return "python";
  }

  @Override
  public boolean accept(@NotNull Project project) {
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (taskManager == null) return false;
    Course course = taskManager.getCourse();
    return course != null && "Python".equals(course.getLanguage()) && "PyCharm".equals(course.getCourseType());
  }
}
