package com.jetbrains.edu.learning.courseGeneration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StudyGenerator {
  private StudyGenerator() {

  }
  private static final Logger LOG = Logger.getInstance(StudyGenerator.class.getName());

  /**
   * Creates task files in its task folder in project user created
   *
   * @param taskDir      project directory of task which task file belongs to
   * @param resourceRoot directory where original task file stored
   * @throws IOException
   */
  public static void createTaskFile(@NotNull final VirtualFile taskDir, @NotNull final File resourceRoot,
                                    @NotNull final String name) throws IOException {
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    File resourceFile = new File(resourceRoot, name);
    File fileInProject = new File(taskDir.getPath(), systemIndependentName);
    FileUtil.copy(resourceFile, fileInProject);
  }

  /**
   * Creates task directory in its lesson folder in project user created
   *
   * @param lessonDir    project directory of lesson which task belongs to
   * @param resourceRoot directory where original task file stored
   * @throws IOException
   */
  public static void createTask(@NotNull final Task task, @NotNull final VirtualFile lessonDir, @NotNull final File resourceRoot,
                                @NotNull final Project project) throws IOException {
    VirtualFile taskDir = lessonDir.createChildDirectory(project, EduNames.TASK_DIR + Integer.toString(task.getIndex() + 1));
    StudyUtils.markDirAsSourceRoot(taskDir, project);
    File newResourceRoot = new File(resourceRoot, taskDir.getName());
    int i = 0;
    for (Map.Entry<String, TaskFile> taskFile : task.getTaskFiles().entrySet()) {
      TaskFile taskFileContent = taskFile.getValue();
      taskFileContent.setIndex(i);
      i++;
      createTaskFile(taskDir, newResourceRoot, taskFile.getKey());
    }
    File[] filesInTask = newResourceRoot.listFiles();
    if (filesInTask != null) {
      for (File file : filesInTask) {
        String fileName = file.getName();
        if (!task.isTaskFile(fileName)) {
          File resourceFile = new File(newResourceRoot, fileName);
          File fileInProject = new File(taskDir.getCanonicalPath(), fileName);
          FileUtil.copy(resourceFile, fileInProject);
        }
      }
    }
  }

  /**
   * Creates lesson directory in its course folder in project user created
   *
   * @param courseDir    project directory of course
   * @param resourceRoot directory where original lesson stored
   * @throws IOException
   */
  public static void createLesson(@NotNull final Lesson lesson, @NotNull final VirtualFile courseDir, @NotNull final File resourceRoot,
                                  @NotNull final Project project) throws IOException {
    String lessonDirName = EduNames.LESSON_DIR + Integer.toString(lesson.getIndex() + 1);
    VirtualFile lessonDir = courseDir.createChildDirectory(project, lessonDirName);
    final List<Task> taskList = lesson.getTaskList();
    for (int i = 0; i < taskList.size(); i++) {
      Task task = taskList.get(i);
      task.setIndex(i);
      createTask(task, lessonDir, new File(resourceRoot, lessonDir.getName()), project);
    }
  }

  /**
   * Creates course directory in project user created
   *
   * @param baseDir      project directory
   * @param resourceRoot directory where original course is stored
   */
  public static void createCourse(@NotNull final Course course, @NotNull final VirtualFile baseDir, @NotNull final File resourceRoot,
                                  @NotNull final Project project) {

              try {
                final List<Lesson> lessons = course.getLessons();
                for (int i = 0; i < lessons.size(); i++) {
                  Lesson lesson = lessons.get(i);
                  lesson.setIndex(i);
                  createLesson(lesson, baseDir, resourceRoot, project);
                }
                baseDir.createChildDirectory(project, EduNames.SANDBOX_DIR);
                File[] files = resourceRoot.listFiles(new FilenameFilter() {
                  @Override
                  public boolean accept(File dir, String name) {
                    return !name.contains(EduNames.LESSON_DIR) && !name.equals("course.json") && !name.equals("hints");
                  }
                });
                for (File file : files) {
                  FileUtil.copy(file, new File(baseDir.getPath(), file.getName()));
                }
              }
              catch (IOException e) {
                LOG.error(e);
              }
  }

  /**
   * Initializes state of course
   */
  public static void initCourse(@NotNull final Course course, boolean isRestarted) {
    for (Lesson lesson : course.lessons) {
      initLesson(lesson, course, isRestarted);
    }
  }

  public static void initLesson(@NotNull final Lesson lesson, final Course course, boolean isRestarted) {
    lesson.setCourse(course);
    final List<Task> taskList = lesson.getTaskList();
    for (Task task : taskList) {
      initTask(task, lesson, isRestarted);
    }
  }

  /**
   * Initializes state of task file
   *
   * @param lesson lesson which task belongs to
   */
  public static void initTask(@NotNull final Task task, final Lesson lesson, boolean isRestarted) {
    task.setLesson(lesson);
    for (TaskFile taskFile : task.getTaskFiles().values()) {
      initTaskFile(taskFile, task, isRestarted);
    }
  }

  public static void initTaskFile(@NotNull final TaskFile taskFile, final Task task, boolean isRestarted) {
    taskFile.setTask(task);
    final List<AnswerPlaceholder> answerPlaceholders = taskFile.getAnswerPlaceholders();
    for (AnswerPlaceholder answerPlaceholder : answerPlaceholders) {
      initAnswerPlaceholder(answerPlaceholder, taskFile, isRestarted);
    }
    Collections.sort(answerPlaceholders, new AnswerPlaceholderComparator());
    for (int i = 0; i < answerPlaceholders.size(); i++) {
      answerPlaceholders.get(i).setIndex(i);
    }
  }

  public static void initAnswerPlaceholder(@NotNull final AnswerPlaceholder placeholder, final TaskFile file, boolean isRestarted) {
    if (!isRestarted) {
      placeholder.setInitialState(new AnswerPlaceholder.MyInitialState(placeholder.getLine(), placeholder.getLength(),
                                                                       placeholder.getStart()));
    }
    placeholder.setTaskFile(file);
  }

}
