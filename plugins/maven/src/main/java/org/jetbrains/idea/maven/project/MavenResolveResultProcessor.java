// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.CleanBrokenArtifactsAndReimportQuickFix;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.RepositoryBlockedSyncIssue;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenArtifactUtilKt;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.idea.maven.model.MavenProjectProblem.ProblemType.REPOSITORY;
import static org.jetbrains.idea.maven.model.MavenProjectProblem.ProblemType.REPOSITORY_BLOCKED;

public class MavenResolveResultProcessor {

  public static ResolveProblem getProblem(@NotNull Collection<MavenProjectReaderResult> results) {
    Map<MavenProjectProblem.ProblemType, Set<String>> repositoryProblemByType = getRepositoryProblems(results);
    if (!repositoryProblemByType.isEmpty()) {
      return new ResolveProblem(
        repositoryProblemByType.getOrDefault(REPOSITORY_BLOCKED, Collections.emptySet()),
        repositoryProblemByType.getOrDefault(REPOSITORY, Collections.emptySet()),
        Collections.emptySet());
    }

    Set<MavenArtifact> unresolvedArtifacts = new HashSet<>();
    for (MavenProjectReaderResult result : results) {
      if (result.mavenModel.getDependencies() != null) {
        for (MavenArtifact artifact : result.mavenModel.getDependencies()) {
          if (!MavenArtifactUtilKt.resolved(artifact)) {
            unresolvedArtifacts.add(artifact);
          }
        }
      }
    }

    return new ResolveProblem(
      Collections.emptySet(),
      Collections.emptySet(),
      unresolvedArtifacts.isEmpty() ? Collections.emptySet() : unresolvedArtifacts
    );
  }

  public static void notifySyncForProblem(@NotNull Project project, @NotNull ResolveProblem problem) {
    MavenSyncConsole syncConsole = MavenProjectsManager.getInstance(project).getSyncConsole();
    for (String repositoryBlockedProblem : problem.repositoryBlockedProblems) {
      BuildIssue buildIssue = RepositoryBlockedSyncIssue.getIssue(project, repositoryBlockedProblem);
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showBuildIssue(repositoryBlockedProblem, buildIssue);
    }
    for (String repositoryProblem : problem.repositoryProblems) {
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showError(repositoryProblem);
    }
    List<Path> files = ContainerUtil.map(problem.unresolvedArtifacts, a -> a.getFile().toPath().getParent());
    CleanBrokenArtifactsAndReimportQuickFix fix = new CleanBrokenArtifactsAndReimportQuickFix(files);
    for (MavenArtifact artifact : problem.unresolvedArtifacts) {
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showBuildIssue(artifact.getMavenId().getKey(), fix);
    }
  }

  public static void notifyMavenProblems(@NotNull Project project) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    MavenSyncConsole syncConsole = projectsManager.getSyncConsole();
    for (MavenProject mavenProject : projectsManager.getProjects()) {
      for (MavenProjectProblem problem : mavenProject.getProblems()) {
        syncConsole.showProblem(problem);
      }
    }
  }

  @NotNull
  private static Map<MavenProjectProblem.ProblemType, Set<String>> getRepositoryProblems(
    @NotNull Collection<MavenProjectReaderResult> results
  ) {
    return results.stream()
      .flatMap(readerResult -> readerResult.readingProblems.stream())
      .filter(problem -> problem.getType() == REPOSITORY_BLOCKED || problem.getType() == REPOSITORY)
      .collect(Collectors.groupingBy(p -> p.getType(), Collectors.mapping(p -> p.getDescription(), Collectors.toSet())));
  }

  public static class ResolveProblem {
    @NotNull
    public final Set<String> repositoryBlockedProblems;
    @NotNull
    public final Set<String> repositoryProblems;
    @NotNull
    public final Set<MavenArtifact> unresolvedArtifacts;

    public ResolveProblem(@NotNull Set<String> repositoryBlockedProblems,
                          @NotNull Set<String> repositoryProblems,
                          @NotNull Set<MavenArtifact> unresolvedArtifacts) {
      this.repositoryBlockedProblems = repositoryBlockedProblems;
      this.repositoryProblems = repositoryProblems;
      this.unresolvedArtifacts = unresolvedArtifacts;
    }

    public boolean isEmpty() {
      return repositoryBlockedProblems.isEmpty() && repositoryProblems.isEmpty() && unresolvedArtifacts.isEmpty();
    }
  }
}
