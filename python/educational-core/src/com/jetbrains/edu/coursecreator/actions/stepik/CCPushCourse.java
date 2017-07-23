package com.jetbrains.edu.coursecreator.actions.stepik;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.stepik.CCStepicConnector;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCPushCourse extends DumbAwareAction {
  public CCPushCourse() {
    super("Upload Course to Stepik", "Upload Course to Stepik", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && CCUtils.isCourseCreator(project));
    if (project != null) {
      final Course course = StudyTaskManager.getInstance(project).getCourse();
      if (course instanceof RemoteCourse) {
        presentation.setText("Update Course on Stepik");
      }
    }
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
    if (course instanceof RemoteCourse) {
      ProgressManager.getInstance().run(new Task.Modal(project, "Updating Course", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          for (Lesson lesson : course.getLessons()) {
            indicator.checkCanceled();
            indicator.setText2("Publishing lesson " + lesson.getIndex());

            if (lesson.getId() > 0) {
              CCStepicConnector.updateLesson(project, lesson);
            }
            else {
              final int lessonId = CCStepicConnector.postLesson(project, lesson);
              if (lessonId != -1) {
                final List<Integer> sections = ((RemoteCourse)course).getSections();
                final Integer sectionId = sections.get(sections.size() - 1);
                CCStepicConnector.postUnit(lessonId, lesson.getIndex(), sectionId, project);
              }
            }
            indicator.setFraction((double)lesson.getIndex()/course.getLessons().size());
          }
        }
      });
    }
    else {
      CCStepicConnector.postCourseWithProgress(project, course);
    }
    EduUsagesCollector.courseUploaded();
  }
}