package com.jetbrains.edu.learning.courseGeneration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StudyGenerator {
  private static final Logger LOG = Logger.getInstance(StudyGenerator.class.getName());

  private StudyGenerator() {}

  public static void createCourse(@NotNull final Course course, @NotNull final VirtualFile baseDir) {
    try {
      final List<Lesson> lessons = course.getLessons(true);
      for (int i = 1; i <= lessons.size(); i++) {
        Lesson lesson = lessons.get(i - 1);
        lesson.setIndex(i);
        createLesson(lesson, baseDir);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static void createLesson(@NotNull final Lesson lesson, @NotNull final VirtualFile courseDir) throws IOException {
    if (EduNames.PYCHARM_ADDITIONAL.equals(lesson.getName())) {
      createAdditionalFiles(lesson, courseDir);
    }
    else {
      String lessonDirName = EduNames.LESSON + Integer.toString(lesson.getIndex());
      VirtualFile lessonDir = courseDir.createChildDirectory(courseDir, lessonDirName);
      final List<Task> taskList = lesson.getTaskList();
      for (int i = 1; i <= taskList.size(); i++) {
        Task task = taskList.get(i - 1);
        task.setIndex(i);
        createTask(task, lessonDir);
      }
    }
  }

  public static void createTask(@NotNull final Task task, @NotNull final VirtualFile lessonDir) throws IOException {
    VirtualFile taskDir = lessonDir.createChildDirectory(lessonDir, EduNames.TASK + Integer.toString(task.getIndex()));
    int i = 0;
    for (Map.Entry<String, TaskFile> taskFile : task.getTaskFiles().entrySet()) {
      TaskFile taskFileContent = taskFile.getValue();
      taskFileContent.setIndex(i);
      i++;
      createTaskFile(taskDir, taskFile.getValue());
    }
    createTestFiles(taskDir, task);
    createDescriptions(taskDir, task);
  }

  public static void createTaskFile(@NotNull final VirtualFile taskDir, @NotNull final TaskFile taskFile) throws IOException {
    final String name = taskFile.name;
    createChildFile(taskDir, name, taskFile.text);
  }

  private static void createDescriptions(VirtualFile taskDir, Task task) throws IOException {
    final Map<String, String> texts = task.getTaskTexts();
    for (Map.Entry<String, String> entry : texts.entrySet()) {
      final String name = entry.getKey();
      final VirtualFile virtualTaskFile = taskDir.createChildData(taskDir, name);
      VfsUtil.saveText(virtualTaskFile, entry.getValue());
    }
  }

  private static void createTestFiles(VirtualFile taskDir, Task task) throws IOException {
    final Map<String, String> tests = task.getTestsText();
    for (Map.Entry<String, String> entry : tests.entrySet()) {
      final String name = entry.getKey();
      final VirtualFile virtualTaskFile = taskDir.createChildData(taskDir, name);
      VfsUtil.saveText(virtualTaskFile, entry.getValue());
    }
  }

  private static void createAdditionalFiles(Lesson lesson, VirtualFile courseDir) throws IOException {
    final List<Task> taskList = lesson.getTaskList();
    if (taskList.size() != 1) return;
    final Task task = taskList.get(0);
    for (Map.Entry<String, String> entry : task.getTestsText().entrySet()) {
      createChildFile(courseDir, entry.getKey(), entry.getValue());
    }
  }


  private static void createChildFile(@NotNull VirtualFile taskDir, String name, String text) throws IOException {
    String newDirectories = null;
    String fileName = name;
    VirtualFile dir = taskDir;
    if (name.contains("/")) {
      int pos = name.lastIndexOf("/");
      fileName = name.substring(pos + 1);
      newDirectories = name.substring(0, pos);
    }
    if (newDirectories != null) {
      dir = VfsUtil.createDirectoryIfMissing(taskDir, newDirectories);
    }
    if (dir != null) {
      final VirtualFile virtualTaskFile = dir.createChildData(taskDir, fileName);
      if (EduUtils.isImage(name)) {
        virtualTaskFile.setBinaryContent(Base64.decodeBase64(text));
      }
      else {
        VfsUtil.saveText(virtualTaskFile, text);
      }
    }
  }
}
