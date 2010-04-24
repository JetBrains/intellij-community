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
package org.jetbrains.idea.maven.embedder;

import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.List;
import java.util.Set;

public class MavenWrapperExecutionResult {
  private final MavenProject myMavenProject;
  private final Set<MavenId> myUnresolvedArtifactIds;
  private final List<Exception> myExceptions;

  public MavenWrapperExecutionResult(MavenProject mavenProject, Set<MavenId> unresolvedArtifactIds, List<Exception> exceptions) {
    myMavenProject = mavenProject;
    myUnresolvedArtifactIds = unresolvedArtifactIds;
    myExceptions = exceptions;
  }

  @Nullable
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
