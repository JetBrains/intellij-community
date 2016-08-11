package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Course {
  @Expose private List<Lesson> lessons = new ArrayList<>();
  @Expose private List<StepicUser> authors = new ArrayList<>();
  @Expose private String description;
  @Expose private String name;
  private String myCourseDirectory = "";
  @Expose private int id;
  @Expose @SerializedName("update_date") private Date myUpdateDate;
  @Expose private boolean isAdaptive = false;
  @Expose @SerializedName("language") private String myLanguage = "Python";

  //this field is used to distinguish ordinary and CheckIO projects,
  //"PyCharm" is used here for historical reasons
  private String courseType = EduNames.PYCHARM;
  private String courseMode = EduNames.STUDY; //this field is used to distinguish study and course creator modes

  public Course() {
  }

  /**
   * Initializes state of course
   */
  public void initCourse(boolean isRestarted) {
    for (Lesson lesson : getLessons()) {
      lesson.initLesson(this, isRestarted);
    }
  }

  public List<Lesson> getLessons() {
    return lessons;
  }

  public void setLessons(List<Lesson> lessons) {
    this.lessons = lessons;
  }
  public void addLessons(List<Lesson> lessons) {
    this.lessons.addAll(lessons);
  }

  public void addLesson(@NotNull final Lesson lesson) {
    lessons.add(lesson);
  }

  public Lesson getLesson(@NotNull final String name) {
    int lessonIndex = EduUtils.getIndex(name, EduNames.LESSON);
    List<Lesson> lessons = getLessons();
    if (!EduUtils.indexIsValid(lessonIndex, lessons)) {
      return null;
    }
    for (Lesson lesson : lessons) {
      if (lesson.getIndex() - 1 == lessonIndex) {
        return lesson;
      }
    }
    return null;
  }

  public Lesson getLesson(int stepicId) {
    for (Lesson lesson : lessons) {
      if (lesson.getId() == stepicId) {
        return lesson;
      }
    }
    return null;
  }

  @NotNull
  public List<StepicUser> getAuthors() {
    return authors;
  }

  public static String getAuthorsString(@NotNull List<StepicUser> authors) {
    return StringUtil.join(authors, StepicUser::getName, ", ");
  }

  @Transient
  public void setAuthorsAsString(String[] authors) {
    this.authors = new ArrayList<>();
    for (String name : authors) {
      final List<String> firstLast = StringUtil.split(name, " ");
      if (!firstLast.isEmpty()) {
        final StepicUser stepicUser = new StepicUser();
        stepicUser.setFirstName(firstLast.remove(0));
        if (firstLast.size() > 0) {
          stepicUser.setLastName(StringUtil.join(firstLast, " "));
        }
        this.authors.add(stepicUser);
      }
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCourseDirectory() {
    return myCourseDirectory;
  }

  public void setCourseDirectory(@NotNull final String courseDirectory) {
    myCourseDirectory = courseDirectory;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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
        task.setUpdateDate(EduStepicConnector.getTaskUpdateDate(task.getStepicId()));
      }
    }
  }

  public void setUpdateDate(Date date) {
    myUpdateDate = date;
  }

  public Date getUpdateDate() {
    return myUpdateDate;
  }

  public Language getLanguageById() {
    return Language.findLanguageByID(getLanguageID());
  }

  /**
   * This method should be used by serialized only
   * Use {@link #getLanguageID()} and {@link #getLanguageVersion()} methods instead
   */
  @Deprecated
  public String getLanguage() {
    return myLanguage;
  }

  public void setLanguage(@NotNull final String language) {
    myLanguage = language;
  }

  public String getLanguageID() {
    return myLanguage.split(" ")[0];
  }

  @Nullable
  public String getLanguageVersion() {
    String[] split = myLanguage.split(" ");
    if (split.length <= 1) {
      return null;
    }
    return split[1];
  }

  public void setAuthors(List<StepicUser> authors) {
    this.authors = authors;
  }

  @NotNull
  public String getCourseType() {
    return courseType;
  }

  public void setCourseType(String courseType) {
    this.courseType = courseType;
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

  public String getCourseMode() {
    return courseMode;
  }

  public void setCourseMode(String courseMode) {
    this.courseMode = courseMode;
  }

  public Course copy() {
    Element element = XmlSerializer.serialize(this);
    Course copy = XmlSerializer.deserialize(element, Course.class);
    if (copy == null) {
      return null;
    }
    copy.initCourse(true);
    return copy;
  }
}
