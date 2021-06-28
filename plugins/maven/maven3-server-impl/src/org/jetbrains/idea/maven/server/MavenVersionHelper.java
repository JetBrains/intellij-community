// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;

import java.rmi.RemoteException;
import java.util.*;

public class MavenVersionHelper {
  public static List<ModelProblem> resolveVersionProblem(MavenProject project, List<ModelProblem> modelProblems) throws RemoteException {
    ArrayList<Dependency> problemDependencies = new ArrayList<Dependency>();
    for (Dependency dependency : project.getDependencies()) {
      if (dependency.getVersion() == null) {
        problemDependencies.add(dependency);
      }
    }
    if (problemDependencies.isEmpty()) return modelProblems;

    Set<String> problemDependenciesSet = new HashSet<String>();
    for (Dependency d : problemDependencies) {
      problemDependenciesSet.add(getDependencyKey(d));
    }
    List<Dependency> correctDependencies = new ArrayList<Dependency>();
    Set<String> solutionDependenciesSet = new HashSet<String>();
    for (Dependency projectDependency : project.getDependencies()) {
      if (projectDependency.getVersion() != null && problemDependenciesSet.contains(getDependencyKey(projectDependency))) {
        solutionDependenciesSet.add(getDependencyKey(projectDependency));
        correctDependencies.add(projectDependency);
        Maven3ServerGlobals.getLogger().print("found version error dependency " + projectDependency);
      }
    }

    if (solutionDependenciesSet.equals(problemDependenciesSet)) {
      return fixProblems(project, modelProblems, problemDependenciesSet, correctDependencies);
    }
    return modelProblems;
  }

  @NotNull
  private static ArrayList<ModelProblem> fixProblems(MavenProject project,
                                                     List<ModelProblem> modelProblems,
                                                     Set<String> problemDependenciesSet,
                                                     List<Dependency> correctDependencies) throws RemoteException {
    project.setDependencies(correctDependencies);
    ArrayList<ModelProblem> resultProblems = new ArrayList<ModelProblem>();
    for (ModelProblem problem : modelProblems) {
      if (!problem.getMessage().contains("version") && !problem.getMessage().contains("is missing")
          && !containDependency(problem.getMessage(), problemDependenciesSet)) {
        resultProblems.add(problem);
      }
      else {
        Maven3ServerGlobals.getLogger().print("remove model problem " + problem);
      }
    }
    return resultProblems;
  }

  private static String getDependencyKey(Dependency dependency) {
    return dependency.getGroupId() + ":" + dependency.getArtifactId();
  }

  private static boolean containDependency(String message, Collection<String> problemDependencyKeys) {
    for (String dependency : problemDependencyKeys) {
      if (message.contains(dependency)) {
        return true;
      }
    }
    return false;
  }
}
