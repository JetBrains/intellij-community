// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenProjectProblem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MavenArtifactResolveResult implements Serializable {
  public final @NotNull List<MavenArtifact> mavenResolvedArtifacts;
  public final @Nullable MavenProjectProblem problem;

  public MavenArtifactResolveResult(@NotNull List<MavenArtifact> artifacts, @Nullable MavenProjectProblem problem) {
    this.mavenResolvedArtifacts = new ArrayList<>(artifacts);
    this.problem = problem;
  }
}
