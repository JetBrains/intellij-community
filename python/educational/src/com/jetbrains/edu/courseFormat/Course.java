package com.jetbrains.edu.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.stepic.CourseInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Course {
  @Expose
  private List<Lesson> lessons = new ArrayList<Lesson>();

  @Expose private String description;
  @Expose private String name;
  private String myCourseDirectory = "";
  @Expose private List<CourseInfo.Author> authors = new ArrayList<CourseInfo.Author>();
  private boolean myUpToDate;

  @Expose @SerializedName("language")
  private String myLanguage="Python";

  private String courseType="PyCharm";

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

  @NotNull
  public List<CourseInfo.Author> getAuthors() {
    return authors;
  }

  public static String getAuthorsString(@NotNull List<CourseInfo.Author> authors) {
    return StringUtil.join(authors, new Function<CourseInfo.Author, String>() {
      @Override
      public String fun(CourseInfo.Author author) {
        return author.getName();
      }
    }, ", ");
  }

  public void setAuthors(String[] authors) {
    this.authors = new ArrayList<CourseInfo.Author>();
    for (String name : authors) {
      final List<String> pair = StringUtil.split(name, " ");
      if (!pair.isEmpty())
        this.authors.add(new CourseInfo.Author(pair.get(0), pair.size() > 1 ? pair.get(1) : ""));
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
    return myUpToDate;
  }

  public void setUpToDate(boolean upToDate) {
    myUpToDate = upToDate;
  }

  public Language getLanguageById() {
    return Language.findLanguageByID(myLanguage);
  }

  public String getLanguage() {
    return myLanguage;
  }

  public void setLanguage(@NotNull final String language) {
    myLanguage = language;
  }

  public void setAuthors(List<CourseInfo.Author> authors) {
    this.authors = authors;
  }

  public String getCourseType() {
    return courseType;
  }

  public void setCourseType(String courseType) {
    this.courseType = courseType;
  }
}
