package com.jetbrains.edu.learning.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import icons.EducationalCoreIcons;

public class StudyUpdateRecommendationAction extends DumbAwareAction {

  public StudyUpdateRecommendationAction() {
    super("Synchronize Course", "Synchronize Course", EducationalCoreIcons.StepikRefresh);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    Course course = StudyTaskManager.getInstance(project).getCourse();
    assert course != null;

    Lesson adaptiveLesson = course.getLessons().get(0);
    assert adaptiveLesson != null;

    int taskNumber = adaptiveLesson.getTaskList().size();
    Task lastRecommendationInCourse = adaptiveLesson.getTaskList().get(taskNumber - 1);
    Task lastRecommendationOnStepik = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> {
        ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
        return StudyUtils.execCancelable(() -> EduAdaptiveStepicConnector.getNextRecommendation(project, (RemoteCourse)course));
      },
      "Synchronizing Course", true, project);

    if (lastRecommendationOnStepik != null && lastRecommendationOnStepik.getStepId() != lastRecommendationInCourse.getStepId()) {
      lastRecommendationOnStepik.initTask(adaptiveLesson, false);
      EduAdaptiveStepicConnector.replaceCurrentTask(project, lastRecommendationOnStepik, adaptiveLesson);
      ApplicationManager.getApplication().invokeLater(() -> {
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
        ProjectView.getInstance(project).refresh();
        StudyNavigator.navigateToTask(project, lastRecommendationOnStepik);
      });
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null || !course.isAdaptive()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setEnabledAndVisible(true);
  }
}
