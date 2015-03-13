package com.intellij.tasks;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * Simple data class that is used to uniquely identify issue on the server by triple (serverType, serverUrl, globalIssueID).
 *
 * @author Mikhail Golubev
 */
@Tag("TaskCoordinates")
public final class TaskCoordinates {
  /**
   * Special sentinel value of {@link #getRepositoryType()} that indicates that parental task was not an issue.
   */
  public static final String LOCAL_REPOSITORY_TYPE = "<LocalTask>";

  private String myRepositoryType = "";
  private String myRepositoryUrl = "";
  private String myGlobalID = "";

  /**
   * For serialization
   */
  public TaskCoordinates() {
  }

  public TaskCoordinates(@NotNull String repositoryType, @NotNull String repositoryUrl, @NotNull String globalID) {
    myRepositoryType = repositoryType;
    myRepositoryUrl = repositoryUrl;
    myGlobalID = globalID;
  }

  @Attribute("type")
  @NotNull
  public String getRepositoryType() {
    return myRepositoryType;
  }

  @Attribute("url")
  @NotNull
  public String getRepositoryUrl() {
    return myRepositoryUrl;
  }

  @Attribute("id")
  @NotNull
  public String getGlobalID() {
    return myGlobalID;
  }

  /**
   * Means that parental task was an issue, but its repository was not found yet, so repository type and repository type URLs are undefined.
   * It's primarily needed for backward compatibility until all serialized local tasks will include complete coordinates.
   */
  public boolean isIncomplete() {
    return StringUtil.isNotEmpty(myRepositoryType) && StringUtil.isNotEmpty(myRepositoryUrl);
  }

  /**
   * Checks that this coordinate has special fake repository type for {@link LocalTask}.
   * @see #LOCAL_REPOSITORY_TYPE
   */
  public boolean isLocal() {
    return myRepositoryType.equals(LOCAL_REPOSITORY_TYPE);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TaskCoordinates that = (TaskCoordinates)o;

    if (!myRepositoryType.equals(that.myRepositoryType)) return false;
    if (!myRepositoryUrl.equals(that.myRepositoryUrl)) return false;
    return myGlobalID.equals(that.myGlobalID);
  }

  @Override
  public int hashCode() {
    int result = myRepositoryType.hashCode();
    result = 31 * result + myRepositoryUrl.hashCode();
    result = 31 * result + myGlobalID.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "TaskCoordinates(type=" + myRepositoryType + ", URL='" + myRepositoryUrl + "', ID=" + myGlobalID + ")";
  }

  public void setRepositoryType(@NotNull String repositoryType) {
    myRepositoryType = repositoryType;
  }

  public void setRepositoryUrl(@NotNull String repositoryUrl) {
    myRepositoryUrl = repositoryUrl;
  }

  public void setGlobalID(@NotNull String globalID) {
    myGlobalID = globalID;
  }
}
