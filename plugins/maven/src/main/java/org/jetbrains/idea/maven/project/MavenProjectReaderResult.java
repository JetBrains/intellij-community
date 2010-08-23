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
package org.jetbrains.idea.maven.project;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class MavenProjectReaderResult {
  public MavenGeneralSettings settings;
  public Collection<MavenProjectProblem> readingProblems;
  public Set<MavenId> unresolvedArtifactIds;
  public File localRepository;
  public MavenProject nativeMavenProject;
  public Collection<String> activatedProfiles;

  public MavenProjectReaderResult(MavenGeneralSettings settings,
                                  Collection<MavenProjectProblem> readingProblems,
                                  Set<MavenId> unresolvedArtifactIds,
                                  File localRepository,
                                  MavenProject nativeMavenProject,
                                  Collection<String> activatedProfiles) {
    this.settings = settings;
    this.readingProblems = readingProblems;
    this.unresolvedArtifactIds = unresolvedArtifactIds;
    this.localRepository = localRepository;
    this.nativeMavenProject = nativeMavenProject;
    this.activatedProfiles = activatedProfiles;
  }
}
