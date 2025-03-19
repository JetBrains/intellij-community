// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class MavenServerExecutionResult implements Serializable {

  public static final MavenServerExecutionResult EMPTY =
    new MavenServerExecutionResult(null, null, Collections.emptyList(), Collections.emptySet());

  public final @Nullable File file;
  public final @Nullable ProjectData projectData;
  public final @NotNull Collection<MavenProjectProblem> problems;
  public final @NotNull Set<MavenId> unresolvedArtifacts;
  public final @NotNull Collection<MavenProjectProblem> unresolvedProblems;

  public MavenServerExecutionResult(@Nullable File file,
                                    @Nullable ProjectData projectData,
                                    @NotNull Collection<MavenProjectProblem> problems,
                                    @NotNull Set<MavenId> unresolvedArtifacts) {
    this(file, projectData, problems, unresolvedArtifacts, Collections.emptyList());
  }

  public MavenServerExecutionResult(@Nullable File file,
                                    @Nullable ProjectData projectData,
                                    @NotNull Collection<MavenProjectProblem> problems,
                                    @NotNull Set<MavenId> unresolvedArtifacts,
                                    @NotNull Collection<MavenProjectProblem> unresolvedProblems) {
    this.file = file;
    this.projectData = projectData;
    this.problems = problems;
    this.unresolvedArtifacts = unresolvedArtifacts;
    this.unresolvedProblems = unresolvedProblems;
  }

  public static class ProjectData implements Serializable {
    public final @NotNull MavenModel mavenModel;
    public final @NotNull List<MavenId> managedDependencies;
    public final String dependencyHash;
    public final boolean dependencyResolutionSkipped;
    public final Map<String, String> mavenModelMap;
    public final Collection<String> activatedProfiles;

    public ProjectData(@NotNull MavenModel mavenModel,
                       @NotNull List<MavenId> managedDependencies,
                       @Nullable String dependencyHash,
                       boolean dependencyResolutionSkipped,
                       Map<String, String> mavenModelMap,
                       Collection<String> activatedProfiles) {
      this.mavenModel = mavenModel;
      this.managedDependencies = managedDependencies;
      this.dependencyHash = dependencyHash;
      this.dependencyResolutionSkipped = dependencyResolutionSkipped;
      this.mavenModelMap = mavenModelMap;
      this.activatedProfiles = activatedProfiles;
    }

    @Override
    public String toString() {
      return "{" +
             "mavenModel=" + mavenModel +
             ", dependencyHash=" + dependencyHash +
             '}';
    }
  }

  @Override
  public String toString() {
    return "{" +
           "projectData=" + projectData +
           ", problems=" + problems +
           ", unresolvedArtifacts=" + unresolvedArtifacts +
           ", unresolvedProblems=" + unresolvedProblems +
           '}';
  }
}
