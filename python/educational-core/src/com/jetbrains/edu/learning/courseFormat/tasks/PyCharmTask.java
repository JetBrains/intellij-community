package com.jetbrains.edu.learning.courseFormat.tasks;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

/**
 * Original PyCharm Edu tasks with local tests and answer placeholders
 */
public class PyCharmTask extends Task {

  public PyCharmTask() {
  }

  public PyCharmTask(@NotNull String name) {
    super(name);
  }

  @Override
  public String getTaskType() {
    return "pycharm";
  }

  @Override
  public StudyTaskChecker getChecker(@NotNull Project project) {
    Course course = getLesson().getCourse();
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    return configurator != null ? configurator.getPyCharmTaskChecker(this, project) : super.getChecker(project);
  }
}