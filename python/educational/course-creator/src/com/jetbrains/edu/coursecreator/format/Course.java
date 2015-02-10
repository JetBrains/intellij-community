package com.jetbrains.edu.coursecreator.format;

import com.google.gson.annotations.Expose;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Course {
  @Expose private List<Lesson> lessons = new ArrayList<Lesson>();
  @Expose private String description;

  @Expose private String name;
  @Expose private String author;

  @Expose private String language;

  private Map<String, Lesson> myLessonsMap = new HashMap<String, Lesson>();

  public Map<String, Lesson> getLessonsMap() {
    return myLessonsMap;
  }

  public Lesson getLesson(@NotNull final String name) {
    return myLessonsMap.get(name);
  }

  public Course() {
  }

  public Course(@NotNull final String name, @NotNull final String author, @NotNull final String description) {
    this.description = description;
    this.name = name;
    this.author = author;
  }

  public List<Lesson> getLessons() {
    return lessons;
  }

  public void addLesson(@NotNull final Lesson lesson, @NotNull final PsiDirectory directory) {
    lessons.add(lesson);
    myLessonsMap.put(directory.getName(), lesson);
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public void init() {
    lessons.clear();
    for (Lesson lesson: myLessonsMap.values()) {
      lessons.add(lesson);
      lesson.init();
    }
    Collections.sort(lessons);
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setLessons(List<Lesson> lessons) {
    this.lessons = lessons;
  }

  public void setLessonsMap(Map<String, Lesson> lessonsMap) {
    myLessonsMap = lessonsMap;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }
}
