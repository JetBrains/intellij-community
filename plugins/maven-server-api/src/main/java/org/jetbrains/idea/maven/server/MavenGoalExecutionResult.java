// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.model.MavenResource;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MavenGoalExecutionResult implements Serializable {
  public final boolean success;
  public final @NotNull File file;
  public final @NotNull Folders folders;
  public final @NotNull Collection<MavenProjectProblem> problems;

  public MavenGoalExecutionResult(boolean success, @NotNull File file, @NotNull Folders folders, @NotNull Collection<MavenProjectProblem> problems) {
    this.success = success;
    this.file = file;
    this.folders = folders;
    this.problems = problems;
  }

  @Override
  public String toString() {
    return "MavenGoalExecutionResult{" +
           "success=" + success +
           ", file=" + file +
           '}';
  }

  public static class Folders implements Serializable {
    private List<String> mySources = Collections.emptyList();
    private List<String> myTestSources = Collections.emptyList();
    private List<MavenResource> myResources = Collections.emptyList();
    private List<MavenResource> myTestResources = Collections.emptyList();

    public List<String> getSources() {
      return mySources == null ? Collections.emptyList() : mySources;
    }

    public void setSources(List<String> sources) {
      mySources = new ArrayList<>(sources);
    }

    public List<String> getTestSources() {
      return myTestSources == null ? Collections.emptyList() : myTestSources;
    }

    public void setTestSources(List<String> testSources) {
      myTestSources = new ArrayList<>(testSources);
    }

    public List<MavenResource> getResources() {
      return myResources;
    }

    public void setResources(List<MavenResource> resources) {
      myResources = new ArrayList<>(resources);
    }

    public List<MavenResource> getTestResources() {
      return myTestResources;
    }

    public void setTestResources(List<MavenResource> testResources) {
      myTestResources = new ArrayList<>(testResources);
    }
  }
}
