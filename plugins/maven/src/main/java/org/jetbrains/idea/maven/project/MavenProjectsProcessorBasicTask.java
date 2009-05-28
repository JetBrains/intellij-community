package org.jetbrains.idea.maven.project;

public abstract class MavenProjectsProcessorBasicTask implements MavenProjectsProcessorTask {
  protected final MavenProject myMavenProject;
  protected final MavenProjectsTree myTree;

  public MavenProjectsProcessorBasicTask(MavenProject mavenProject, MavenProjectsTree tree) {
    myMavenProject = mavenProject;
    myTree = tree;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myMavenProject == ((MavenProjectsProcessorBasicTask)o).myMavenProject;
  }

  @Override
  public int hashCode() {
    return myMavenProject.hashCode();
  }
}