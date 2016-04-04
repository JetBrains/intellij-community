package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

public class CCPushLesson extends DumbAwareAction {
  public CCPushLesson() {
    super("Update Lesson on Stepic", "Update Lesson on Stepic", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = CCProjectService.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    PsiDirectory lessonDir = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (lessonDir == null || !lessonDir.getName().contains("lesson")) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson != null && lesson.id > 0) {
      e.getPresentation().setEnabledAndVisible(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = CCProjectService.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    PsiDirectory lessonDir = DirectoryChooserUtil.getOrChooseDirectory(view);
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
        indicator.setText("Uploading lesson to http://stepic.org");
        EduStepicConnector.updateLesson(project, lesson, indicator);
      }});
  }

}