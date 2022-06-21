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
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenProjectProblem;

import java.io.Serializable;
import java.util.List;

public class MavenArtifactResolveResult implements Serializable {
  @NotNull public final List<MavenArtifact> mavenResolvedArtifacts;
  @Nullable public final MavenProjectProblem problem;

  public MavenArtifactResolveResult(@NotNull List<MavenArtifact> artifacts, @Nullable MavenProjectProblem problem) {
    this.mavenResolvedArtifacts = artifacts;
    this.problem = problem;
  }
}
