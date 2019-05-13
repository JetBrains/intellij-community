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

import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenExecutionResult {
  private File myPomFile;
  private final MavenProject myMavenProject;
  private final List<Exception> myExceptions;
  private final DependencyResolutionResult myDependencyResolutionResult;

  public MavenExecutionResult(@Nullable MavenProject mavenProject, List<Exception> exceptions) {
    this(mavenProject, null, exceptions);
  }

  public MavenExecutionResult(List<Exception> exceptions) {
    this(null, null, exceptions);
  }

  public MavenExecutionResult(@Nullable File pomFile, List<Exception> exceptions) {
    this(null, null, exceptions);
    myPomFile = pomFile;
  }

  public MavenExecutionResult(@Nullable MavenProject mavenProject,
                              @Nullable DependencyResolutionResult dependencyResolutionResult,
                              List<Exception> exceptions) {
    myMavenProject = mavenProject;
    if (mavenProject != null) {
      myPomFile = mavenProject.getFile();
    }
    myExceptions = exceptions == null ? new ArrayList<Exception>() : exceptions;
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

  @Nullable
  public File getPomFile() {
    return myMavenProject != null ? myMavenProject.getFile() : myPomFile;
  }
}
