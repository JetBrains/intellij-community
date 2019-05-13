/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class MavenProjectReaderResult {
  @NotNull
  public final MavenModel mavenModel;
  public final Map<String, String> nativeModelMap;
  public final MavenExplicitProfiles activatedProfiles;
  @Nullable public final NativeMavenProjectHolder nativeMavenProject;
  public final Collection<MavenProjectProblem> readingProblems;
  public final Set<MavenId> unresolvedArtifactIds;

  public MavenProjectReaderResult(@NotNull MavenModel mavenModel,
                                  Map<String, String> nativeModelMap,
                                  MavenExplicitProfiles activatedProfiles,
                                  @Nullable NativeMavenProjectHolder nativeMavenProject,
                                  Collection<MavenProjectProblem> readingProblems,
                                  Set<MavenId> unresolvedArtifactIds) {
    this.mavenModel = mavenModel;
    this.nativeModelMap = nativeModelMap;
    this.activatedProfiles = activatedProfiles;
    this.nativeMavenProject = nativeMavenProject;
    this.readingProblems = readingProblems;
    this.unresolvedArtifactIds = unresolvedArtifactIds;
  }
}
