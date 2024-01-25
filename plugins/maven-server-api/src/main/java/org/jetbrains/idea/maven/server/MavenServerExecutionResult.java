/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class MavenServerExecutionResult implements Serializable {
  @Nullable public final ProjectData projectData;
  @NotNull public final Collection<MavenProjectProblem> problems;
  @NotNull public final Set<MavenId> unresolvedArtifacts;
  @NotNull public final Collection<MavenProjectProblem> unresolvedProblems;

  public MavenServerExecutionResult(@Nullable ProjectData projectData,
                                    @NotNull Collection<MavenProjectProblem> problems,
                                    @NotNull Set<MavenId> unresolvedArtifacts) {
    this(projectData, problems, unresolvedArtifacts, Collections.emptyList());
  }

  public MavenServerExecutionResult(@Nullable ProjectData projectData,
                                    @NotNull Collection<MavenProjectProblem> problems,
                                    @NotNull Set<MavenId> unresolvedArtifacts,
                                    @NotNull Collection<MavenProjectProblem> unresolvedProblems) {
    this.projectData = projectData;
    this.problems = problems;
    this.unresolvedArtifacts = unresolvedArtifacts;
    this.unresolvedProblems = unresolvedProblems;
  }

  public static class ProjectData implements Serializable {
    @NotNull
    public final MavenModel mavenModel;
    public final String dependencyHash;
    public final Map<String, String> mavenModelMap;
    public final NativeMavenProjectHolder nativeMavenProject;
    public final Collection<String> activatedProfiles;

    public ProjectData(@NotNull MavenModel mavenModel,
                       @Nullable String dependencyHash,
                       Map<String, String> mavenModelMap,
                       NativeMavenProjectHolder nativeMavenProject,
                       Collection<String> activatedProfiles) {
      this.mavenModel = mavenModel;
      this.dependencyHash = dependencyHash;
      this.mavenModelMap = mavenModelMap;
      this.nativeMavenProject = nativeMavenProject;
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
