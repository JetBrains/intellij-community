// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.model.MavenSource;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private List<MavenSource> myMavenSources = Collections.emptyList();

    public void setMavenSources(List<MavenSource> mavenSources) {
      myMavenSources = mavenSources;
    }

    public List<MavenSource> getMavenSources() {
      return myMavenSources;
    }

    public List<String> getSources() {
      return myMavenSources.stream().filter(it -> MavenSource.isSource(it)).map(it -> it.getDirectory()).collect(Collectors.toList());
    }


    public List<String> getTestSources() {
      return myMavenSources.stream().filter(it -> MavenSource.isTestSource(it)).map(it -> it.getDirectory()).collect(Collectors.toList());
    }

    public void set(List<String> sources, List<String> testSources, List<MavenResource> resources, List<MavenResource> testResources) {
      myMavenSources = new ArrayList<>();
      sources.forEach(it -> myMavenSources.add(MavenSource.fromSrc(it, false)));
      testSources.forEach(it -> myMavenSources.add(MavenSource.fromSrc(it, true)));
      resources.forEach(it -> myMavenSources.add(MavenSource.fromResource(it, false)));
      testResources.forEach(it -> myMavenSources.add(MavenSource.fromResource(it, true)));
    }

    public List<MavenResource> getTestResources() {
      return myMavenSources.stream().filter(it -> MavenSource.isTestResource(it)).map(it -> new MavenResource(it))
        .collect(Collectors.toList());
    }

    public List<MavenResource> getResources() {
      return myMavenSources.stream().filter(it -> MavenSource.isResource(it)).map(it -> new MavenResource(it))
        .collect(Collectors.toList());
    }
  }
}
