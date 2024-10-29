// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.services.ModelBuilderException;
import org.apache.maven.model.building.DefaultModelProblem;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;

import java.io.File;
import java.util.Collections;
import java.util.List;

public final class Maven40ResolverUtil {
  public static void handleProjectBuildingException(List<ProjectBuildingResult> buildingResults, ProjectBuildingException e) {
    List<ProjectBuildingResult> results = e.getResults();
    if (results != null && !results.isEmpty()) {
      buildingResults.addAll(results);
    }
    else {
      Throwable cause = e.getCause();
      if (cause instanceof ModelBuildingException) {
        buildingResults.add(new MyProjectBuildingResult(null, e.getPomFile(), null, ((ModelBuildingException)cause).getProblems(), null));
      }
      else if (cause instanceof ModelBuilderException) {
        buildingResults.add(new MyProjectBuildingResult(null, e.getPomFile(), null, MavenApiConverterUtil.convertFromApiProblems(
          ((ModelBuilderException)cause).getProblems()), null));
      }
      else {
        buildingResults.add(new MyProjectBuildingResult(null, e.getPomFile(), null, Collections.singletonList(
          new DefaultModelProblem(cause.getMessage(), ModelProblem.Severity.FATAL, ModelProblem.Version.BASE, "", 0, 0, null, e)
        ), null));
      }
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
