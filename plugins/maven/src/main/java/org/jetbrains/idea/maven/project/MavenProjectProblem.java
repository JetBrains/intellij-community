package org.jetbrains.idea.maven.project;

import java.io.Serializable;

public class MavenProjectProblem implements Serializable {
  private String myDescription;
  private boolean isCritical;

  protected MavenProjectProblem() {
  }

  public MavenProjectProblem(String description, boolean critical) {
    myDescription = description;
    isCritical = critical;
  }

  public String getDescription() {
    return myDescription;
  }

  public boolean isCritical() {
    return isCritical;
  }

  @Override
  public String toString() {
    return (isCritical ? "!!!" : "") + myDescription;
  }
}
