/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class Maven3ExecutionResult {
  private File myPomFile;
  private final MavenProject myMavenProject;
  private final List<Exception> myExceptions;
  private final List<ModelProblem> myModelProblems;
  private final DependencyResolutionResult myDependencyResolutionResult;
  private String dependencyHash;

  public Maven3ExecutionResult(@Nullable MavenProject mavenProject, List<Exception> exceptions) {
    this(mavenProject, null, exceptions, Collections.emptyList());
  }

  public Maven3ExecutionResult(List<Exception> exceptions) {
    this(null, null, exceptions, Collections.emptyList());
  }

  public Maven3ExecutionResult(@Nullable File pomFile, List<Exception> exceptions) {
    this(null, null, exceptions, Collections.emptyList());
    myPomFile = pomFile;
  }

  public Maven3ExecutionResult(@Nullable MavenProject mavenProject,
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

  public boolean hasExceptions() {
    return !myExceptions.isEmpty();
  }

  public List<ModelProblem> getModelProblems() {
    return myModelProblems;
  }

  @Nullable
  public File getPomFile() {
    return myMavenProject != null ? myMavenProject.getFile() : myPomFile;
  }

  public String getDependencyHash() {
    return dependencyHash;
  }

  public void setDependencyHash(String dependencyHash) {
    this.dependencyHash = dependencyHash;
  }
}
