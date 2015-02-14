package com.jetbrains.edu.courseFormat;

import com.google.gson.annotations.SerializedName;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Lesson {
  @Transient
  String id;
  @Transient
  public List<Integer> steps;
  @Transient
  public List<String> tags;
  @Transient
  Boolean is_public;
  @SerializedName("title")
  private String name;

  public List<Task> taskList = new ArrayList<Task>();

  @Transient
  private Course myCourse = null;
  private int myIndex = -1;

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
}
