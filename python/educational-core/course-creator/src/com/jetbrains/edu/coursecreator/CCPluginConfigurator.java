package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.actions.CCShowHintAction;
import com.jetbrains.edu.learning.StudyBasePluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.edu.coursecreator.CCUtils.COURSE_MODE;

public class CCPluginConfigurator extends StudyBasePluginConfigurator {

  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StudyPreviousTaskAction());
    group.add(new StudyNextTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new CCShowHintAction());

    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());
    return group;
  }

  @Override
  public boolean accept(@NotNull Project project) {
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    final Course course = taskManager.getCourse();
    if (course != null) {
      final String mode = course.getCourseMode();
      return COURSE_MODE.equals(mode);
    }
    return false;
  }
}
