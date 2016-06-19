package com.jetbrains.edu.learning.stepic;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of class which contains information to be shawn in course description in tool window
 * and when project is being created
 */
public class CourseInfo {
  public static CourseInfo INVALID_COURSE = new CourseInfo();

  @SerializedName("title") private String myName;
  int id;
  boolean isAdaptive;
  boolean isPublic;
  List<Integer> sections;
  List<Integer> instructors = new ArrayList<Integer>();

  List<StepicUser> myAuthors = new ArrayList<>();
  @SerializedName("summary") private String myDescription;
  @SerializedName("course_format") private String myType = "pycharm Python"; //course type in format "pycharm <language>"
  @Nullable private String username;

  public String getName() {
    return myName;
  }

  @NotNull
  public List<StepicUser> getAuthors() {
    return myAuthors;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getType() {
    return myType;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CourseInfo that = (CourseInfo)o;
    if (that.getName() == null || that.getDescription() == null) return false;
    return that.getName().equals(getName())
           && that.getDescription().equals(myDescription);
  }

  @Override
  public int hashCode() {
    int result = getName() != null ? getName().hashCode() : 0;
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    return result;
  }

  @Nullable
  public String getUsername() {
    return username;
  }

  public void setUsername(@Nullable String username) {
    this.username = username;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setAuthors(List<StepicUser> authors) {
    myAuthors = authors;
    for (StepicUser author : authors) {
      if (author.id > 0) {
        instructors.add(author.id);
      }
    }
  }

  public void addAuthor(StepicUser author) {
    if (myAuthors == null) {
      myAuthors = new ArrayList<>();
    }
    myAuthors.add(author);
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public void setType(String type) {
    myType = type;
  }

  public boolean isAdaptive() {
    return isAdaptive;
  }

  public void setAdaptive(boolean adaptive) {
    isAdaptive = adaptive;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public void setPublic(boolean aPublic) {
    isPublic = aPublic;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }
}
