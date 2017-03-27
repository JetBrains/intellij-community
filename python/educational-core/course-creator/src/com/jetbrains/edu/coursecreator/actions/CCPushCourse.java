package com.jetbrains.edu.coursecreator.actions;

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
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepic.CCStepicConnector;
import com.jetbrains.edu.learning.stepic.CourseInfo;
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
      if (course != null) {
        final int id = course.getId();
        if (id > 0) {
          presentation.setText("Update Course on Stepik");
        }
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
    if (course.getId() > 0) {
      ProgressManager.getInstance().run(new Task.Modal(project, "Updating Course", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          for (Lesson lesson : course.getLessons()) {
            if (lesson.getId() > 0) {
              CCStepicConnector.updateLesson(project, lesson, indicator);
            }
            else {
              final CourseInfo info = CourseInfo.fromCourse(course);
              final int lessonId = CCStepicConnector.postLesson(project, lesson, indicator);
              if (lessonId != -1) {
                final List<Integer> sections = info.getSections();
                final Integer sectionId = sections.get(sections.size() - 1);
                CCStepicConnector.postUnit(lessonId, lesson.getIndex(), sectionId);
              }
            }
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