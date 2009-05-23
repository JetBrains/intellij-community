package org.jetbrains.idea.maven.project;

import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenId;

import java.io.File;
import java.util.List;
import java.util.Set;

public class MavenProjectReaderResult {
  public boolean isValid;
  public List<String> activeProfiles;
  public List<MavenProjectProblem> readingProblems;
  public Set<MavenId> unresolvedArtifactIds;
  public File localRepository;
  public MavenProject nativeMavenProject;

  public MavenProjectReaderResult(boolean valid,
                                  List<String> activeProfiles,
                                  List<MavenProjectProblem> readingProblems,
                                  Set<MavenId> unresolvedArtifactIds,
                                  File localRepository,
                                  MavenProject nativeMavenProject) {
    isValid = valid;
    this.activeProfiles = activeProfiles;
    this.readingProblems = readingProblems;
    this.unresolvedArtifactIds = unresolvedArtifactIds;
    this.localRepository = localRepository;
    this.nativeMavenProject = nativeMavenProject;
  }
}
