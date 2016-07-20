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
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

public class CCPushTask extends DumbAwareAction {
  public CCPushTask() {
    super("Update Task on Stepic", "Update Task on Stepic", null);
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
    if (course == null) {
      return;
    }
    PsiDirectory taskDir = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (taskDir == null || !taskDir.getName().contains("task")) {
      return;
    }
    final PsiDirectory lessonDir = taskDir.getParentDirectory();
    if (lessonDir == null) return;
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson != null && lesson.getId() > 0) {
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
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    PsiDirectory taskDir = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (taskDir == null || !taskDir.getName().contains("task")) {
      return;
    }
    final PsiDirectory lessonDir = taskDir.getParentDirectory();
    if (lessonDir == null) return;
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) return;

    final com.jetbrains.edu.learning.courseFormat.Task task = lesson.getTask(taskDir.getName());
    if (task == null) return;

    ProgressManager.getInstance().run(new Task.Modal(project, "Uploading Task", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Uploading task to http://stepic.org");
        EduStepicConnector.updateTask(project, task);
      }
    });
  }

}