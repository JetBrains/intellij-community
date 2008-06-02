package org.jetbrains.idea.maven.project;

public class MavenProjectModelProblem {
  private String myDescription;
  private boolean isCritical;

  public MavenProjectModelProblem(String description, boolean critical) {
    myDescription = description;
    isCritical = critical;
  }

  public String getDescription() {
    return myDescription;
  }

  public boolean isCritical() {
    return isCritical;
  }
}
