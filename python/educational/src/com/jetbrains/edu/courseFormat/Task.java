package com.jetbrains.edu.courseFormat;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.StudyNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of task which contains task files, tests, input file for tests
 */
public class Task implements StudyStateful {
  private String name;
  private int myIndex;
  public Map<String, TaskFile> taskFiles = new HashMap<String, TaskFile>();

  private String text;
  private String testsText;

  @Transient private Lesson myLesson;
  private List<UserTest> userTests = new ArrayList<UserTest>();


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getText() {
    return text;
  }

  public void setText(@NotNull final String text) {
    this.text = text;
  }

  public int getIndex() {
    return myIndex;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public String getTestsText() {
    return testsText;
  }

  public void setTestsText(@NotNull final String testsText) {
    this.testsText = testsText;
  }



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

  public void setStatus(@NotNull final StudyStatus status) {
    for (TaskFile taskFile : taskFiles.values()) {
      taskFile.setStatus(status);
    }
  }

  public List<UserTest> getUserTests() {
    return userTests;
  }

  public void setUserTests(@NotNull final List<UserTest> userTests) {
    this.userTests = userTests;
  }

  public boolean isTaskFile(@NotNull final String fileName) {
    return taskFiles.get(fileName) != null;
  }

  @Nullable
  public TaskFile getFile(@NotNull final String fileName) {
    return taskFiles.get(fileName);
  }

  @Transient
  public Lesson getLesson() {
    return myLesson;
  }

  @Transient
  public void setLesson(Lesson lesson) {
    myLesson = lesson;
  }

  @Nullable
  public VirtualFile getTaskDir(Project project) {
    String lessonDirName = StudyNames.LESSON_DIR + String.valueOf(myLesson.getIndex() + 1);
    String taskDirName = StudyNames.TASK_DIR + String.valueOf(myIndex + 1);
    VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      VirtualFile lessonDir = courseDir.findChild(lessonDirName);
      if (lessonDir != null) {
        return lessonDir.findChild(taskDirName);
      }
    }
    return null;
  }
}
