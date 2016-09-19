package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.stepic.CCStepicConnector;
import com.jetbrains.edu.learning.stepic.CourseInfo;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

import static com.jetbrains.edu.coursecreator.actions.CCFromCourseArchive.createAnswerFile;
import static com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator.OUR_COURSES_DIR;
import static com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator.flushCourse;

public class CCGetCourseFromStepic extends DumbAwareAction {

  public CCGetCourseFromStepic() {
    super("Get Course From Stepik", "Get Course From Stepik", null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final String courseId = Messages.showInputDialog("Please, enter course id", "Get Course From Stepik", null);
    if (StringUtil.isNotEmpty(courseId)) {
      ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Modal(project, "Creating Course", true) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          createCourse(project, courseId);
        }
      });
    }
  }

  private static void createCourse(Project project, String courseId) {
    final VirtualFile baseDir = project.getBaseDir();
    final CourseInfo info = CCStepicConnector.getCourseInfo(project, courseId);
    if (info == null) return;

    final Course course = EduStepicConnector.getCourse(project, info);
    if (course != null) {
      flushCourse(project, course);

      final File courseDirectory = StudyUtils.getCourseDirectory(project, course);
      ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
        final VirtualFile[] children = baseDir.getChildren();
        for (VirtualFile child : children) {
          StudyUtils.deleteFile(child);
        }
        StudyGenerator.createCourse(course, baseDir, courseDirectory, project);
      }), ModalityState.current());


      StudyTaskManager.getInstance(project).setCourse(course);
      File courseDir = new File(OUR_COURSES_DIR, course.getName() + "-" + project.getName());
      course.setCourseDirectory(courseDir.getPath());
      course.setCourseMode(CCUtils.COURSE_MODE);
      project.getBaseDir().refresh(false, true);
      int index = 1;
      int taskIndex = 1;
      for (Lesson lesson : course.getLessons()) {
        final VirtualFile lessonDir = project.getBaseDir().findChild(EduNames.LESSON + String.valueOf(index));
        lesson.setIndex(index);
        if (lessonDir == null) continue;
        for (Task task : lesson.getTaskList()) {
          final VirtualFile taskDir = lessonDir.findChild(EduNames.TASK + String.valueOf(taskIndex));
          task.setIndex(taskIndex);
          task.setLesson(lesson);
          if (taskDir == null) continue;
          for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
            ApplicationManager.getApplication()
              .invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> createAnswerFile(project, taskDir, entry)),
                             ModalityState.current());
          }
          taskIndex += 1;
        }
        index += 1;
        taskIndex = 1;
      }
      course.initCourse(true);
      ApplicationManager.getApplication()
        .invokeAndWait(() -> StudyUtils.registerStudyToolWindow(course, project), ModalityState.current());
    }
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }
}