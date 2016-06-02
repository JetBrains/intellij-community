package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

public class CCPushCourse extends DumbAwareAction {
  public CCPushCourse() {
    super("Upload Course to Stepic", "Upload Course to Stepic", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && CCUtils.isCourseCreator(project));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    EduStepicConnector.postCourseWithProgress(project, course);
  }

}