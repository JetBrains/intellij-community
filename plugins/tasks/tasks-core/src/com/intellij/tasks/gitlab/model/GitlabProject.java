package com.intellij.tasks.gitlab.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Only required fields are declared.
 * Field {@code name} and {@code webUrl} may be null because only {@code id} is serialized.
 *
 * @author Mikhail Golubev
 */
@Tag("GitlabProject")
public class GitlabProject {
  private int id;
  private String name;
  @SerializedName("web_url")
  private String webUrl;

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GitlabProject)) return false;
    return id == ((GitlabProject)o).id;
  }

  @Override
  public final int hashCode() {
    return id;
  }

  @Attribute("id")
  public int getId() {
    return id;
  }

  /**
   * For serialization purposes only
   */
  public void setId(int id) {
    this.id = id;
  }

  @Nullable
  public String getName() {
    return name;
  }

  @Nullable
  public String getWebUrl() {
    return webUrl;
  }

  @Override
  public final String toString() {
    return getName();
  }
}
