package com.jetbrains.edu.learning.stepic;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of class which contains information to be shawn in course description in tool window
 * and when project is being created
 */
public class CourseInfo {
  @SerializedName("title")
  private String myName;
  
  @SerializedName("summary")
  private String myDescription;
  
  @SerializedName("course_format")
  //course type in format "pycharm <language>"
  private String myType = "pycharm Python";

  private boolean isAdaptive;
  
  int id;
  boolean is_public;
  public List<Integer> sections;
  List<Integer> instructors = new ArrayList<Integer>();
  List<StepicUser> myAuthors = new ArrayList<>();

  public static CourseInfo INVALID_COURSE = new CourseInfo();

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

  public static class Author {
    int id;
    String first_name = "";
    String last_name = "";

    public Author() {}

    public Author(String firstName, String lastName) {
      first_name = firstName;
      last_name = lastName;
    }

    public String getName() {
      return StringUtil.join(new String[]{first_name, last_name}, " ");
    }

    public int getId() {
      return id;
    }
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
}
