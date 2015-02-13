package com.jetbrains.edu.courseFormat;

import com.google.gson.annotations.SerializedName;
import com.intellij.util.xmlb.annotations.Transient;

import java.util.ArrayList;
import java.util.List;

public class Lesson implements StudyStateful {
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
  public void setStatus(StudyStatus status) {
    for (Task task : taskList) {
      task.setStatus(status);
    }
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
}
