package org.jetbrains.idea.maven.embedder;

import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.List;
import java.util.Set;

public class MavenExecutionResult {
  private final MavenProject myMavenProject;
  private final Set<MavenId> myUnresolvedArtifactIds;
  private final List<Exception> myExceptions;

  public MavenExecutionResult(MavenProject mavenProject, Set<MavenId> unresolvedArtifactIds, List<Exception> exceptions) {
    myMavenProject = mavenProject;
    myUnresolvedArtifactIds = unresolvedArtifactIds;
    myExceptions = exceptions;
  }

  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  public Set<MavenId> getUnresolvedArtifactIds() {
    return myUnresolvedArtifactIds;
  }

  public List<Exception> getExceptions() {
    return myExceptions;
  }

  public boolean hasExceptions() {
    return !myExceptions.isEmpty();
  }
}
