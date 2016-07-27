package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Lesson implements StudyItem {
  @Expose private int myId;
  @Transient public List<Integer> steps;
  @Transient public List<String> tags;
  @Transient boolean is_public;
  @Transient int position;

  @Expose
  @SerializedName("title")
  private String name;

  @Expose
  @SerializedName("task_list")
  public List<Task> taskList = new ArrayList<Task>();

  @Transient
  private Course myCourse = null;

  // index is visible to user number of lesson from 1 to lesson number
  private int myIndex = -1;

  public Lesson() {
  }

  public void initLesson(final Course course, boolean isRestarted) {
    setCourse(course);
    for (Task task : getTaskList()) {
      task.initTask(this, isRestarted);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getIndex() {
    return myIndex;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public List<Task> getTaskList() {
    return taskList;
  }

  @Transient
  public Course getCourse() {
    return myCourse;
  }

  @Transient
  public void setCourse(Course course) {
    myCourse = course;
  }

  public void addTask(@NotNull final Task task) {
    taskList.add(task);
  }

  public Task getTask(@NotNull final String name) {
    int index = EduUtils.getIndex(name, EduNames.TASK);
    List<Task> tasks = getTaskList();
    if (!EduUtils.indexIsValid(index, tasks)) {
      return null;
    }
    for (Task task : tasks) {
      if (task.getIndex() - 1 == index) {
        return task;
      }
    }
    return null;
  }

  public StudyStatus getStatus() {
    for (Task task : taskList) {
      if (task.getStatus() != StudyStatus.Solved) {
        return StudyStatus.Unchecked;
      }
    }
    return StudyStatus.Solved;
  }

  public int getId() {
    return myId;
  }

  public void setId(int id) {
    this.myId = id;
  }
}
