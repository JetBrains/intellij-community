package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RemoteCourse extends Course {
  //course type in format "pycharm<version> <language>"
  @SerializedName("course_format") private String myType = "pycharm" + EduStepicConnector.CURRENT_VERSION + " Python";
  @SerializedName("is_idea_compatible") private boolean isCompatible = true;
  List<Integer> sections;
  List<Integer> instructors = new ArrayList<>();
  @Expose private int id;
  @Expose @SerializedName("update_date") private Date myUpdateDate;
  @Expose private boolean isAdaptive = false;
  @SerializedName("is_public") boolean isPublic;

  public String getType() {
    return myType;
  }

  public List<Integer> getSections() {
    return sections;
  }

  public void setSections(List<Integer> sections) {
    this.sections = sections;
  }

  public void setInstructors(List<Integer> instructors) {
    this.instructors = instructors;
  }

  public List<Integer> getInstructors() {
    return instructors;
  }


  public boolean isUpToDate() {
    if (id == 0) return true;
    if (!EduNames.STUDY.equals(courseMode)) return true;
    final Date date = EduStepicConnector.getCourseUpdateDate(id);
    if (date == null) return true;
    if (myUpdateDate == null) return true;
    if (date.after(myUpdateDate)) return false;
    for (Lesson lesson : lessons) {
      if (!lesson.isUpToDate()) return false;
    }
    return true;
  }

  public void setUpdated() {
    setUpdateDate(EduStepicConnector.getCourseUpdateDate(id));
    for (Lesson lesson : lessons) {
      lesson.setUpdateDate(EduStepicConnector.getLessonUpdateDate(lesson.getId()));
      for (Task task : lesson.getTaskList()) {
        task.setUpdateDate(EduStepicConnector.getTaskUpdateDate(task.getStepId()));
      }
    }
  }

  public void setUpdateDate(Date date) {
    myUpdateDate = date;
  }

  public Date getUpdateDate() {
    return myUpdateDate;
  }

  public boolean isAdaptive() {
    return isAdaptive;
  }

  public void setAdaptive(boolean adaptive) {
    isAdaptive = adaptive;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public void copyCourseParameters(RemoteCourse course) {
    setName(course.getName());

    setUpdateDate(course.getUpdateDate());

  }
}
