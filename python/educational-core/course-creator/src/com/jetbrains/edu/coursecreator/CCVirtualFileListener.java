package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class CCVirtualFileListener extends VirtualFileAdapter {

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    VirtualFile createdFile = event.getFile();
    if (createdFile.isDirectory()) {
      return;
    }
    if (createdFile.getPath().contains(CCUtils.GENERATED_FILES_FOLDER)) {
      return;
    }
    Project project = ProjectUtil.guessProjectForFile(createdFile);
    if (project == null) {
      return;
    }
    if (project.getBasePath() !=null && !FileUtil.isAncestor(project.getBasePath(), createdFile.getPath(), true)) {
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

    CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
    if (manager != null && manager.doNotPackFile(new File(createdFile.getPath()))) {
      return;
    }

    if (CCUtils.isTestsFile(project, createdFile)
        || StudyUtils.isTaskDescriptionFile(name)
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

    CCUtils.createResourceFile(createdFile, course, taskVF);

    task.addTaskFile(name, 1);
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    VirtualFile removedFile = event.getFile();
    String path = removedFile.getPath();
    if (path.contains(CCUtils.GENERATED_FILES_FOLDER)) {
      return;
    }

    Project project = ProjectUtil.guessProjectForFile(removedFile);
    if (project == null) {
      return;
    }
    if (project.getBasePath() !=null && !FileUtil.isAncestor(project.getBasePath(), removedFile.getPath(), true)) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null || path.contains(FileUtil.toSystemIndependentName(course.getCourseDirectory()))) {
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
    CCUtils.updateHigherElements(courseDir.getChildren(), file -> course.getLesson(file.getName()), removedLesson.getIndex(), EduNames.LESSON, -1);
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
    CCUtils.updateHigherElements(lessonDir.getChildren(), file -> lesson.getTask(file.getName()), task.getIndex(), EduNames.TASK, -1);
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
