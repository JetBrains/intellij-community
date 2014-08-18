package com.jetbrains.python.edu.course;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Lesson implements Stateful{
  public String name;
  public List<Task> taskList = new ArrayList<Task>();
  private Course myCourse = null;
  public int myIndex = -1;
  public static final String LESSON_DIR = "lesson";
  public LessonInfo myLessonInfo = new LessonInfo();

  public LessonInfo getLessonInfo() {
    return myLessonInfo;
  }

  @Transient
  public StudyStatus getStatus() {
    for (Task task : taskList) {
      StudyStatus taskStatus = task.getStatus();
      if (taskStatus == StudyStatus.Unchecked || taskStatus == StudyStatus.Failed) {
        return StudyStatus.Unchecked;
      }
    }
    return StudyStatus.Solved;
  }

  @Override
  public void setStatus(StudyStatus status, StudyStatus oldStatus) {
    for (Task task : taskList) {
      task.setStatus(status, oldStatus);
    }
  }

  public List<Task> getTaskList() {
    return taskList;
  }


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Creates lesson directory in its course folder in project user created
   *
   * @param courseDir    project directory of course
   * @param resourceRoot directory where original lesson stored
   * @throws java.io.IOException
   */
  public void create(@NotNull final VirtualFile courseDir, @NotNull final File resourceRoot) throws IOException {
    String lessonDirName = LESSON_DIR + Integer.toString(myIndex + 1);
    VirtualFile lessonDir = courseDir.createChildDirectory(this, lessonDirName);
    for (int i = 0; i < taskList.size(); i++) {
      Task task = taskList.get(i);
      task.setIndex(i);
      task.create(lessonDir, new File(resourceRoot, lessonDir.getName()));
    }
  }


  /**
   * Initializes state of lesson
   *
   * @param course course which lesson belongs to
   */
  public void init(final Course course, boolean isRestarted) {
    myCourse = course;
    myLessonInfo.setTaskNum(taskList.size());
    myLessonInfo.setTaskUnchecked(taskList.size());
    for (Task task : taskList) {
      task.init(this, isRestarted);
    }
  }

  public Lesson next() {
    List<Lesson> lessons = myCourse.getLessons();
    if (myIndex + 1 >= lessons.size()) {
      return null;
    }
    return lessons.get(myIndex + 1);
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  public Lesson prev() {
    if (myIndex - 1 < 0) {
      return null;
    }
    return myCourse.getLessons().get(myIndex - 1);
  }
}
