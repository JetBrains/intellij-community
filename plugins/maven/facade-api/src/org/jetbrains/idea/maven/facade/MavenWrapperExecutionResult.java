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
package org.jetbrains.idea.maven.facade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class MavenWrapperExecutionResult implements Serializable {
  @Nullable public final ProjectData projectData;
  @NotNull public final Collection<MavenProjectProblem> problems;
  @NotNull public final Set<MavenId> unresolvedArtifacts;

  public MavenWrapperExecutionResult(ProjectData projectData,
                                     Collection<MavenProjectProblem> problems,
                                     Set<MavenId> unresolvedArtifacts) {
    this.projectData = projectData;
    this.problems = problems;
    this.unresolvedArtifacts = unresolvedArtifacts;
  }

  public static class ProjectData implements Serializable {
    public final MavenModel mavenModel;
    public final Map<String, String> mavenModelMap;
    public final NativeMavenProjectHolder nativeMavenProject;
    public final Collection<String> activatedProfiles;

    public ProjectData(MavenModel mavenModel,
                       Map<String, String> mavenModelMap,
                       NativeMavenProjectHolder nativeMavenProject,
                       Collection<String> activatedProfiles) {
      this.mavenModel = mavenModel;
      this.mavenModelMap = mavenModelMap;
      this.nativeMavenProject = nativeMavenProject;
      this.activatedProfiles = activatedProfiles;
    }
  }
}
