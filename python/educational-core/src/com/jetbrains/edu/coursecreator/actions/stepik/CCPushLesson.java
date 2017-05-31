package com.jetbrains.edu.coursecreator.actions.stepik;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.stepik.CCStepicConnector;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.stepic.EduStepicNames;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCPushLesson extends DumbAwareAction {
  public CCPushLesson() {
    super("Update Lesson on Stepik", "Update Lesson on Stepik", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null || !(course instanceof RemoteCourse)) {
      return;
    }
    if (!course.getCourseMode().equals(CCUtils.COURSE_MODE)) return;
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0 || directories.length > 1) {
      return;
    }

    final PsiDirectory lessonDir = directories[0];
    if (lessonDir == null || !lessonDir.getName().contains("lesson")) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson != null && ((RemoteCourse)course).getId() > 0) {
      e.getPresentation().setEnabledAndVisible(true);
      if (lesson.getId() <= 0) {
        e.getPresentation().setText("Upload Lesson to Stepik");
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
    if (course == null || !(course instanceof RemoteCourse)) {
      return;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0 || directories.length > 1) {
      return;
    }

    final PsiDirectory lessonDir = directories[0];
    if (lessonDir == null || !lessonDir.getName().contains("lesson")) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return;
    }
    ProgressManager.getInstance().run(new Task.Modal(project, "Uploading Lesson", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Uploading lesson to " + EduStepicNames.STEPIC_URL);
        if (lesson.getId() > 0) {
          CCStepicConnector.updateLesson(project, lesson);
        }
        else {
          final int lessonId = CCStepicConnector.postLesson(project, lesson);
          final List<Integer> sections = ((RemoteCourse)course).getSections();
          final Integer sectionId = sections.get(sections.size()-1);
          CCStepicConnector.postUnit(lessonId, lesson.getIndex(), sectionId, project);
        }
      }});
  }

}