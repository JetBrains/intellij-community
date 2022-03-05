// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.RepositoryBlockedSyncIssue;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public class MavenResolveResultProcessor {

  private static final String BLOCKED_MIRROR_FOR_REPOSITORIES = "Blocked mirror for repositories:";

  public static void notifySyncForProblem(@NotNull Project project, @NotNull ArtifactTransferProblems problem) {
    MavenSyncConsole syncConsole = MavenProjectsManager.getInstance(project).getSyncConsole();
    for (MavenProjectProblem projectProblem : problem.repositoryBlockedProblems) {
      if (projectProblem.getDescription() == null) continue;
      BuildIssue buildIssue = RepositoryBlockedSyncIssue.getIssue(project, projectProblem.getDescription());
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showBuildIssue(buildIssue.getTitle(), buildIssue);
    }

    for (MavenProjectProblem projectProblem : problem.unresolvedArtifactPronlems) {
      if (projectProblem.getMavenArtifact() == null || projectProblem.getDescription() == null) continue;
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showArtifactBuildIssue(projectProblem.getMavenArtifact().getMavenId().getKey(), projectProblem.getDescription());
    }
  }

  public static void notifySyncForProblem(@NotNull Project project, @NotNull MavenProjectProblem problem) {
    MavenSyncConsole syncConsole = MavenProjectsManager.getInstance(project).getSyncConsole();
    String message = problem.getDescription();
    if (message == null) return;

    if (message.contains(BLOCKED_MIRROR_FOR_REPOSITORIES)) {
      BuildIssue buildIssue = RepositoryBlockedSyncIssue.getIssue(project, problem.getDescription());
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showBuildIssue(buildIssue.getTitle(), buildIssue);
    } else if (problem.getMavenArtifact() == null) {
      MavenProjectsManager.getInstance(project).getSyncConsole()
        .addWarning(SyncBundle.message("maven.sync.annotation.processor.problem"), message);
    }

    if (problem.getMavenArtifact() != null) {
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY)
        .showArtifactBuildIssue(problem.getMavenArtifact().getMavenId().getKey(), message);
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
  public static ArtifactTransferProblems getArtifactTransferProblems(@NotNull Collection<MavenProjectReaderResult> results) {
    Set<MavenProjectProblem> repositoryBlockedProblems = new HashSet<>();
    Set<MavenProjectProblem> unresolvedArtifactProblems = new HashSet<>();
    Set<MavenArtifact> unresolvedArtifacts = new HashSet<>();

    boolean hasProblem = false;
    for (MavenProjectReaderResult result : results) {
      for (MavenProjectProblem problem : result.readingProblems) {
        if (!hasProblem) hasProblem = true;
        if (problem.getMavenArtifact() != null) {
          unresolvedArtifactProblems.add(problem);
          unresolvedArtifacts.add(problem.getMavenArtifact());
        }
        String message = problem.getDescription();
        if (message != null && message.contains(BLOCKED_MIRROR_FOR_REPOSITORIES)) {
          repositoryBlockedProblems.add(problem);
        }
      }
    }
    return new ArtifactTransferProblems(repositoryBlockedProblems, unresolvedArtifactProblems, unresolvedArtifacts);
  }

  public static class ArtifactTransferProblems {
    @NotNull
    public final Set<MavenProjectProblem> repositoryBlockedProblems;
    @NotNull
    public final Set<MavenProjectProblem> unresolvedArtifactPronlems;
    @NotNull
    public final Set<MavenArtifact> unresolvedArtifacts;

    public ArtifactTransferProblems(@NotNull Set<MavenProjectProblem> repositoryBlockedProblems,
                                    @NotNull Set<MavenProjectProblem> unresolvedArtifactPronlems,
                                    @NotNull Set<MavenArtifact> unresolvedArtifacts) {
      this.repositoryBlockedProblems = repositoryBlockedProblems;
      this.unresolvedArtifactPronlems = unresolvedArtifactPronlems;
      this.unresolvedArtifacts = unresolvedArtifacts;
    }

    public boolean isEmpty() {
      return repositoryBlockedProblems.isEmpty()
             && unresolvedArtifactPronlems.isEmpty() && unresolvedArtifacts.isEmpty();
    }
  }
}
