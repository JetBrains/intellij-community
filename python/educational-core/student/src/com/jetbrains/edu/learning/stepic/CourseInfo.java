package com.jetbrains.edu.learning.stepic;

import com.google.gson.annotations.SerializedName;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of class which contains information to be shawn in course description in tool window
 * and when project is being created
 */
public class CourseInfo {
  public static final CourseInfo INVALID_COURSE = new CourseInfo();

  @SerializedName("title") private String myName;
  int id;
  boolean isAdaptive;
  @SerializedName("is_public") boolean isPublic;
  List<Integer> sections;
  List<Integer> instructors = new ArrayList<>();

  List<StepicUser> myAuthors = new ArrayList<>();
  @SerializedName("summary") private String myDescription;
  @SerializedName("course_format") private String myType = "pycharm Python"; //course type in format "pycharm <language>"
  @Nullable private String username;

  @SerializedName("update_date") private Date updateDate;
  @SerializedName("is_idea_compatible") private boolean isCompatible = true;

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
      if (author.getId() > 0) {
        instructors.add(author.getId());
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

  public Date getUpdateDate() {
    return updateDate;
  }

  public void setUpdateDate(Date updateDate) {
    this.updateDate = updateDate;
  }

  public static CourseInfo fromCourse(@Nullable final Course course) {
    if (course == null) return null;
    final List<CourseInfo> infos = StudyProjectGenerator.getCoursesFromCache().stream().
      filter(info -> info.id == course.getId()).collect(Collectors.toList());
    if (infos.isEmpty()) return null;
    return infos.get(0);
  }

  public List<Integer> getSections() {
    return sections;
  }

  public boolean isCompatible() {
    return isCompatible;
  }

  public void setCompatible(boolean compatible) {
    isCompatible = compatible;
  }
}
