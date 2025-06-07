// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Maven3ExecutionResult {
  private final MavenProject myMavenProject;
  private final List<Exception> myExceptions;

  public Maven3ExecutionResult(@Nullable MavenProject mavenProject, @NotNull List<Exception> exceptions) {
    myMavenProject = mavenProject;
    myExceptions = exceptions;
  }

  public @Nullable MavenProject getMavenProject() {
    return myMavenProject;
  }

  public @NotNull List<Exception> getExceptions() {
    return myExceptions;
  }
}
