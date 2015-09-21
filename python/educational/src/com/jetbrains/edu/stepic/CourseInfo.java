package com.jetbrains.edu.stepic;

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
  boolean is_public;
  public List<Integer> sections;
  @SerializedName("title")
  private String myName;
  @SerializedName("summary")
  private String myDescription;
  @SerializedName("course_format")
  //course type in format "pycharm <language>"
  private String myType = "pycharm Python";

  List<Integer> instructors = new ArrayList<Integer>();

  List<Author> myAuthors = new ArrayList<Author>();
  int id;

  public static CourseInfo INVALID_COURSE = new CourseInfo();

  public String getName() {
    return myName;
  }

  @NotNull
  public List<Author> getAuthors() {
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

  public void setAuthors(List<Author> authors) {
    myAuthors = authors;
    for (Author author : authors) {
      if (author.id > 0) {
        instructors.add(author.id);
      }
    }
  }

  public void addAuthor(Author author) {
    if (myAuthors == null) {
      myAuthors = new ArrayList<Author>();
    }
    myAuthors.add(author);
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public void setType(String type) {
    myType = type;
  }
}
