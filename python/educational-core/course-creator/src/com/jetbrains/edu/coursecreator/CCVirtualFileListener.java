package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.util.Function;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class CCVirtualFileListener extends VirtualFileAdapter {

  private static final Logger LOG = Logger.getInstance(CCVirtualFileListener.class);

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    VirtualFile createdFile = event.getFile();
    Project project = ProjectUtil.guessProjectForFile(createdFile);
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null || !CCUtils.isCourseCreator(project)) {
      return;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, createdFile);
    if (taskFile != null) {
      return;
    }

    String name = createdFile.getName();
    if (CCUtils.isTestsFile(project, createdFile)
        || EduNames.TASK_HTML.equals(name)
        || name.contains(EduNames.WINDOW_POSTFIX)
        || name.contains(EduNames.WINDOWS_POSTFIX)
        || name.contains(EduNames.ANSWERS_POSTFIX)) {
      return;
    }

    VirtualFile taskVF = createdFile.getParent();
    if (taskVF == null) {
      return;
    }
    Task task = StudyUtils.getTask(project, taskVF);
    if (task == null) {
      return;
    }

    createResourceFile(createdFile, course, taskVF);

    task.addTaskFile(name, 1);
  }

  private static void createResourceFile(VirtualFile createdFile, Course course, VirtualFile taskVF) {
    VirtualFile lessonVF = taskVF.getParent();
    if (lessonVF == null) {
      return;
    }

    String taskResourcesPath = FileUtil.join(course.getCourseDirectory(), lessonVF.getName(), taskVF.getName());
    File taskResourceFile = new File(taskResourcesPath);
    if (!taskResourceFile.exists()) {
      if (!taskResourceFile.mkdirs()) {
        LOG.info("Failed to create resources for task " + taskResourcesPath);
      }
    }
    try {
      File toFile = new File(taskResourceFile, createdFile.getName());
      FileUtil.copy(new File(createdFile.getPath()), toFile);
    }
    catch (IOException e) {
      LOG.info("Failed to copy created task file to resources " + createdFile.getPath());
    }
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    VirtualFile removedFile = event.getFile();
    if (removedFile.getPath().contains(CCUtils.GENERATED_FILES_FOLDER)) {
      return;
    }

    Project project = ProjectUtil.guessProjectForFile(removedFile);
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final TaskFile taskFile = StudyUtils.getTaskFile(project, removedFile);
    if (taskFile != null) {
      deleteTaskFile(removedFile, taskFile);
      return;
    }
    if (removedFile.getName().contains(EduNames.TASK)) {
      deleteTask(course, removedFile);
    }
    if (removedFile.getName().contains(EduNames.LESSON)) {
      deleteLesson(course, removedFile, project);
    }
  }

  private static void deleteLesson(@NotNull final Course course, @NotNull final VirtualFile removedLessonFile, Project project) {
    Lesson removedLesson = course.getLesson(removedLessonFile.getName());
    if (removedLesson == null) {
      return;
    }
    VirtualFile courseDir = project.getBaseDir();
    CCUtils.updateHigherElements(courseDir.getChildren(), new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        return course.getLesson(file.getName());
      }
    }, removedLesson.getIndex(), EduNames.LESSON, -1);
    course.getLessons().remove(removedLesson);
  }

  private static void deleteTask(@NotNull final Course course, @NotNull final VirtualFile removedTask) {
    VirtualFile lessonDir = removedTask.getParent();
    if (lessonDir == null || !lessonDir.getName().contains(EduNames.LESSON)) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return;
    }
    Task task = lesson.getTask(removedTask.getName());
    if (task == null) {
      return;
    }
    CCUtils.updateHigherElements(lessonDir.getChildren(), new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        return lesson.getTask(file.getName());
      }
    }, task.getIndex(), EduNames.TASK, -1);
    lesson.getTaskList().remove(task);
  }

  private static void deleteTaskFile(@NotNull final VirtualFile removedTaskFile, TaskFile taskFile) {
    Task task = taskFile.getTask();
    if (task == null) {
      return;
    }
    task.getTaskFiles().remove(removedTaskFile.getName());
  }
}
