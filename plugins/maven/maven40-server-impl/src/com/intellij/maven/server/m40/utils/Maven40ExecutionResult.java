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
  private File myPomFile;
  private final MavenProject myMavenProject;
  private final List<Exception> myExceptions;
  private final List<ModelProblem> myModelProblems;
  private final DependencyResolutionResult myDependencyResolutionResult;
  private String checksum;

  public Maven40ExecutionResult(@Nullable MavenProject mavenProject, List<Exception> exceptions) {
    this(mavenProject, null, exceptions, Collections.emptyList());
  }

  public Maven40ExecutionResult(List<Exception> exceptions) {
    this(null, null, exceptions, Collections.emptyList());
  }

  public Maven40ExecutionResult(@Nullable File pomFile, List<Exception> exceptions) {
    this(null, null, exceptions, Collections.emptyList());
    myPomFile = pomFile;
  }

  public Maven40ExecutionResult(@Nullable MavenProject mavenProject,
                                @Nullable DependencyResolutionResult dependencyResolutionResult,
                                @NotNull List<Exception> exceptions,
                                @NotNull List<ModelProblem> modelProblems) {
    myMavenProject = mavenProject;
    myModelProblems = modelProblems;
    if (mavenProject != null) {
      myPomFile = mavenProject.getFile();
    }
    myExceptions = exceptions;
    myDependencyResolutionResult = dependencyResolutionResult;
    if(myDependencyResolutionResult != null && myDependencyResolutionResult.getCollectionErrors() != null) {
      myExceptions.addAll(myDependencyResolutionResult.getCollectionErrors());
    }
  }

  @Nullable
  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  @Nullable
  public DependencyResolutionResult getDependencyResolutionResult() {
    return myDependencyResolutionResult;
  }

  @NotNull
  public List<Exception> getExceptions() {
    return myExceptions;
  }

  public List<ModelProblem> getModelProblems() {
    return myModelProblems;
  }

  @Nullable
  public File getPomFile() {
    return myMavenProject != null ? myMavenProject.getFile() : myPomFile;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
  }
}
