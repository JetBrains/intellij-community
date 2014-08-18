package com.jetbrains.python.edu.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.edu.StudyUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of task which contains task files, tests, input file for tests
 */
public class Task implements Stateful{
  public static final String TASK_DIR = "task";
  private static final String ourTestFile = "tests.py";
  public String name;
  private static final String ourTextFile = "task.html";
  public Map<String, TaskFile> taskFiles = new HashMap<String, TaskFile>();
  private Lesson myLesson;
  public int myIndex;
  public List<UserTest> userTests = new ArrayList<UserTest>();
  public static final String USER_TESTS = "userTests";

  public Map<String, TaskFile> getTaskFiles() {
    return taskFiles;
  }

  @Transient
  public StudyStatus getStatus() {
    for (TaskFile taskFile : taskFiles.values()) {
      StudyStatus taskFileStatus = taskFile.getStatus();
      if (taskFileStatus == StudyStatus.Unchecked) {
        return StudyStatus.Unchecked;
      }
      if (taskFileStatus == StudyStatus.Failed) {
        return StudyStatus.Failed;
      }
    }
    return StudyStatus.Solved;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setStatus(@NotNull final StudyStatus status, @NotNull final StudyStatus oldStatus) {
    LessonInfo lessonInfo = myLesson.getLessonInfo();
    if (status != oldStatus) {
      lessonInfo.update(oldStatus, -1);
      lessonInfo.update(status, +1);
    }
    for (TaskFile taskFile : taskFiles.values()) {
      taskFile.setStatus(status, oldStatus);
    }
  }

  public List<UserTest> getUserTests() {
    return userTests;
  }

  public String getTestFile() {
    return ourTestFile;
  }

  public String getText() {
    return ourTextFile;
  }

  /**
   * Creates task directory in its lesson folder in project user created
   *
   * @param lessonDir    project directory of lesson which task belongs to
   * @param resourceRoot directory where original task file stored
   * @throws java.io.IOException
   */
  public void create(@NotNull final VirtualFile lessonDir, @NotNull final File resourceRoot) throws IOException {
    VirtualFile taskDir = lessonDir.createChildDirectory(this, TASK_DIR + Integer.toString(myIndex + 1));
    File newResourceRoot = new File(resourceRoot, taskDir.getName());
    int i = 0;
    for (Map.Entry<String, TaskFile> taskFile : taskFiles.entrySet()) {
      TaskFile taskFileContent = taskFile.getValue();
      taskFileContent.setIndex(i);
      i++;
      taskFileContent.create(taskDir, newResourceRoot, taskFile.getKey());
    }
    File[] filesInTask = newResourceRoot.listFiles();
    if (filesInTask != null) {
      for (File file : filesInTask) {
        String fileName = file.getName();
        if (!isTaskFile(fileName)) {
          File resourceFile = new File(newResourceRoot, fileName);
          File fileInProject = new File(taskDir.getCanonicalPath(), fileName);
          FileUtil.copy(resourceFile, fileInProject);
        }
      }
    }
  }

  private boolean isTaskFile(@NotNull final String fileName) {
    return taskFiles.get(fileName) != null;
  }

  @Nullable
  public TaskFile getFile(@NotNull final String fileName) {
    return taskFiles.get(fileName);
  }

  /**
   * Initializes state of task file
   *
   * @param lesson lesson which task belongs to
   */
  public void init(final Lesson lesson, boolean isRestarted) {
    myLesson = lesson;
    for (TaskFile taskFile : taskFiles.values()) {
      taskFile.init(this, isRestarted);
    }
  }

  public Task next() {
    Lesson currentLesson = this.myLesson;
    List<Task> taskList = myLesson.getTaskList();
    if (myIndex + 1 < taskList.size()) {
      return taskList.get(myIndex + 1);
    }
    Lesson nextLesson = currentLesson.next();
    if (nextLesson == null) {
      return null;
    }
    return StudyUtils.getFirst(nextLesson.getTaskList());
  }

  public Task prev() {
    Lesson currentLesson = this.myLesson;
    if (myIndex - 1 >= 0) {
      return myLesson.getTaskList().get(myIndex - 1);
    }
    Lesson prevLesson = currentLesson.prev();
    if (prevLesson == null) {
      return null;
    }
    //getting last task in previous lesson
    return prevLesson.getTaskList().get(prevLesson.getTaskList().size() - 1);
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  public Lesson getLesson() {
    return myLesson;
  }


  @Nullable
  public VirtualFile getTaskDir(Project project) {
    String lessonDirName = Lesson.LESSON_DIR + String.valueOf(myLesson.getIndex() + 1);
    String taskDirName = TASK_DIR + String.valueOf(myIndex + 1);
    VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      VirtualFile lessonDir = courseDir.findChild(lessonDirName);
      if (lessonDir != null) {
        return lessonDir.findChild(taskDirName);
      }
    }
    return null;
  }

  /**
   * Gets text of resource file such as test input file or task text in needed format
   *
   * @param fileName name of resource file which should exist in task directory
   * @param wrapHTML if it's necessary to wrap text with html tags
   * @return text of resource file wrapped with html tags if necessary
   */
  @Nullable
  public String getResourceText(@NotNull final Project project, @NotNull final String fileName, boolean wrapHTML) {
    VirtualFile taskDir = getTaskDir(project);
    if (taskDir != null) {
      return StudyUtils.getFileText(taskDir.getCanonicalPath(), fileName, wrapHTML);
    }
    return null;
  }

}
