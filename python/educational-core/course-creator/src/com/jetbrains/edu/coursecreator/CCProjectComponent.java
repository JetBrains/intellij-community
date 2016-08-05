package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CCProjectComponent extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance(CCProjectComponent.class);
  private final CCVirtualFileListener myTaskFileLifeListener = new CCVirtualFileListener();
  private final Project myProject;

  protected CCProjectComponent(Project project) {
    super(project);
    myProject = project;
  }

  public void migrateIfNeeded() {
    Course studyCourse = StudyTaskManager.getInstance(myProject).getCourse();
    if (studyCourse == null) {
      Course oldCourse = CCProjectService.getInstance(myProject).getCourse();
      if (oldCourse == null) {
        return;
      }
      StudyTaskManager.getInstance(myProject).setCourse(oldCourse);
      CCProjectService.getInstance(myProject).setCourse(null);
      oldCourse.initCourse(true);
      oldCourse.setCourseMode(CCUtils.COURSE_MODE);
      File coursesDir = new File(PathManager.getConfigPath(), "courses");
      File courseDir = new File(coursesDir, oldCourse.getName() + "-" + myProject.getName());
      oldCourse.setCourseDirectory(courseDir.getPath());
      StudyUtils.registerStudyToolWindow(oldCourse, myProject);
      transformFiles(oldCourse, myProject);
    }
  }

  private static void transformFiles(Course course, Project project) {
    List<VirtualFile> files = getAllAnswerTaskFiles(course, project);
    for (VirtualFile answerFile : files) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        String answerName = answerFile.getName();
        String nameWithoutExtension = FileUtil.getNameWithoutExtension(answerName);
        String name = FileUtil.getNameWithoutExtension(nameWithoutExtension) + "." + FileUtilRt.getExtension(answerName);
        VirtualFile parent = answerFile.getParent();
        VirtualFile file = parent.findChild(name);
        try {
          if (file != null) {
            file.delete(CCProjectComponent.class);
          }
          VirtualFile windowsDescrFile = parent.findChild(FileUtil.getNameWithoutExtension(name) + EduNames.WINDOWS_POSTFIX);
          if (windowsDescrFile != null) {
            windowsDescrFile.delete(CCProjectComponent.class);
          }
          answerFile.rename(CCProjectComponent.class, name);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      });
    }
  }


  private static List<VirtualFile> getAllAnswerTaskFiles(@NotNull Course course, @NotNull Project project) {
    List<VirtualFile> result = new ArrayList<>();
    for (Lesson lesson : course.getLessons()) {
      for (Task task : lesson.getTaskList()) {
        for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          String name = entry.getKey();
          String answerName = FileUtil.getNameWithoutExtension(name) + CCUtils.ANSWER_EXTENSION_DOTTED + FileUtilRt.getExtension(name);
          String taskPath = FileUtil.join(project.getBasePath(), EduNames.LESSON + lesson.getIndex(), EduNames.TASK + task.getIndex());
          VirtualFile taskFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.join(taskPath, answerName));
          if (taskFile == null) {
            taskFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.join(taskPath, EduNames.SRC, answerName));
          }
          if (taskFile != null) {
            result.add(taskFile);
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public String getComponentName() {
    return "CCProjectComponent";
  }

  public void projectOpened() {
    migrateIfNeeded();
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
    EduUsagesCollector.projectTypeOpened(CCUtils.COURSE_MODE);
  }

  public void projectClosed() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myTaskFileLifeListener);
  }
}
