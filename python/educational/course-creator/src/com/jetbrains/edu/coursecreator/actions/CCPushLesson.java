package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

public class CCPushLesson extends DumbAwareAction {
  public CCPushLesson() {
    super("Push lesson to Stepic", "Push lesson to Stepic", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    CCProjectService.setCCActionAvailable(e);
    final DataContext dataContext = e.getDataContext();
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    final Project project = e.getProject();
    if (virtualFile == null || !virtualFile.isDirectory() || (project != null && virtualFile.equals(project.getBaseDir()))) {
      EduUtils.enableAction(e, false);
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
    ProgressManager.getInstance().run(new Task.Modal(project, "Uploading Course", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Uploading course to http://stepic.org");
        EduStepicConnector.postLesson(project, lesson, indicator);
      }});
  }

}