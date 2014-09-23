package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Course {
  @Expose public List<Lesson> lessons = new ArrayList<Lesson>();
  @Expose public String description;

  @Expose public String name;
  @Expose public String author;

  public Map<String, Lesson> myLessonsMap = new HashMap<String, Lesson>();

  public Map<String, Lesson> getLessonsMap() {
    return myLessonsMap;
  }

  public Lesson getLesson(@NotNull final  String name) {
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
}
