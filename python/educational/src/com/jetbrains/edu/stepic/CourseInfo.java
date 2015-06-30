package com.jetbrains.edu.stepic;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of class which contains information to be shawn in course description in tool window
 * and when project is being created
 */
public class CourseInfo {
  boolean is_public;
  public List<Integer> sections;
  @SerializedName("title")
  private String myName;
  @SerializedName("summary")
  private String myDescription;
  @SerializedName("course_format")
  //course type in format "pycharm <language>"
  private String myType;

  @SerializedName("instructors")
  List<Instructor> myInstructors = new ArrayList<Instructor>();

  public static CourseInfo INVALID_COURSE = new CourseInfo();

  public String getName() {
    return myName;
  }

  @NotNull
  public List<Instructor> getInstructors() {
    return myInstructors;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getType() {
    return myType;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CourseInfo that = (CourseInfo)o;
    if (that.getName() == null || that.getDescription() == null) return false;
    return that.getName().equals(myName)
           && that.getDescription().equals(myDescription);
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    return result;
  }

  public static class Instructor {
    String name;

    public Instructor(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public void setName(String name) {
    myName = name;
  }

  public void setInstructors(List<Instructor> instructors) {
    myInstructors = instructors;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public void setType(String type) {
    myType = type;
  }
}
