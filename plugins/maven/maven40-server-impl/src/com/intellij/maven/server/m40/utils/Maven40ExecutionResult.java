// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class Maven40ExecutionResult {
  private final MavenProject myMavenProject;
  private final List<Exception> myExceptions;

  public Maven40ExecutionResult(@Nullable MavenProject mavenProject, @NotNull List<Exception> exceptions) {
    myMavenProject = mavenProject;
    myExceptions = exceptions;
  }

  @Nullable
  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  @NotNull
  public List<Exception> getExceptions() {
    return myExceptions;
  }
}
