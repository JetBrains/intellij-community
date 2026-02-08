// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;

import java.io.File;
import java.util.List;

public final class Maven3ResolverUtil {
  public static void handleProjectBuildingException(List<ProjectBuildingResult> buildingResults, ProjectBuildingException e) {
    List<ProjectBuildingResult> results = e.getResults();
    if (results != null && !results.isEmpty()) {
      buildingResults.addAll(results);
    }
    else {
      Throwable cause = e.getCause();
      List<ModelProblem> problems = null;
      if (cause instanceof ModelBuildingException) {
        problems = ((ModelBuildingException)cause).getProblems();
      }
      buildingResults.add(new MyProjectBuildingResult(null, e.getPomFile(), null, problems, null));
    }
  }

  private static class MyProjectBuildingResult implements ProjectBuildingResult {

    private final String myProjectId;
    private final File myPomFile;
    private final MavenProject myMavenProject;
    private final List<ModelProblem> myProblems;
    private final DependencyResolutionResult myDependencyResolutionResult;

    MyProjectBuildingResult(String projectId,
                            File pomFile,
                            MavenProject mavenProject,
                            List<ModelProblem> problems,
                            DependencyResolutionResult dependencyResolutionResult) {
      myProjectId = projectId;
      myPomFile = pomFile;
      myMavenProject = mavenProject;
      myProblems = problems;
      myDependencyResolutionResult = dependencyResolutionResult;
    }

    @Override
    public String getProjectId() {
      return myProjectId;
    }

    @Override
    public File getPomFile() {
      return myPomFile;
    }

    @Override
    public MavenProject getProject() {
      return myMavenProject;
    }

    @Override
    public List<ModelProblem> getProblems() {
      return myProblems;
    }

    @Override
    public DependencyResolutionResult getDependencyResolutionResult() {
      return myDependencyResolutionResult;
    }
  }
}
