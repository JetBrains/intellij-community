package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.EduStepicNames;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RemoteCourse extends Course {
  //course type in format "pycharm<version> <language>"
  @SerializedName("course_format") private String myType =
                        String.format("%s%d %s", EduStepicNames.PYCHARM_PREFIX, EduStepicConnector.CURRENT_VERSION, getLanguageID());
  @SerializedName("is_idea_compatible") private boolean isCompatible = true;
  List<Integer> sections;
  List<Integer> instructors = new ArrayList<>();
  @Expose private int id;
  @Expose @SerializedName("update_date") private Date myUpdateDate;
  private Boolean isUpToDate = true;
  @Expose private boolean isAdaptive = false;
  @Expose @SerializedName("is_public") boolean isPublic;

  public String getType() {
    return myType;
  }

  public void setLanguage(@NotNull final String language) {
    super.setLanguage(language);
    updateType(language);
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

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(new Backgroundable(null, "Updating Course") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final Date date = EduStepicConnector.getCourseUpdateDate(id);
        if (date == null) return;
        if (date.after(myUpdateDate)) {
          isUpToDate = false;
        }
        for (Lesson lesson : lessons) {
          if (!lesson.isUpToDate()) {
            isUpToDate = false;
          }
        }
      }
    }, new EmptyProgressIndicator());

    return isUpToDate;
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

  private void updateType(String language) {
    myType = String.format("%s%d %s", EduStepicNames.PYCHARM_PREFIX, EduStepicConnector.CURRENT_VERSION, language);
  }

  public boolean isPublic() {
    return isPublic;
  }
}
