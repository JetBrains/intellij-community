package com.jetbrains.edu.courseFormat;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Course {
  private List<Lesson> lessons = new ArrayList<Lesson>();

  private String description;
  private String name;
  private String myCourseDirectory = "";
  private String author="";
  private boolean myUpToDate;
  private String myLanguage;

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

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
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
}
