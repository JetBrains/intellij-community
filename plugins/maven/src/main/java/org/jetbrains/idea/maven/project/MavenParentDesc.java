package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;

public class MavenParentDesc {
  private MavenId myParentId;
  private String myParentRelativePath;

  public MavenParentDesc(@NotNull MavenId parentId, @NotNull String parentRelativePath) {
    myParentId = parentId;
    myParentRelativePath = parentRelativePath;
  }

  @NotNull
  public MavenId getParentId() {
    return myParentId;
  }

  @NotNull
  public String getParentRelativePath() {
    return myParentRelativePath;
  }
}
