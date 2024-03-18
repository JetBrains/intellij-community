// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;

import java.util.Set;

@ApiStatus.Internal
public final class MavenResolveResultProblemProcessor {
  public static final String BLOCKED_MIRROR_FOR_REPOSITORIES = "Blocked mirror for repositories:";

  public static void notifySyncForProblem(@NotNull Project project, @NotNull MavenProjectProblem problem) {
    MavenSyncConsole syncConsole = MavenProjectsManager.getInstance(project).getSyncConsole();
    String message = problem.getDescription();
    if (message == null) return;

    if (message.contains(BLOCKED_MIRROR_FOR_REPOSITORIES)) {
      BuildIssue buildIssue = RepositoryBlockedSyncIssue.getIssue(project, problem.getDescription());
      syncConsole.showBuildIssue(buildIssue);
    } else if (problem.getMavenArtifact() == null) {
      MavenProjectsManager.getInstance(project).getSyncConsole()
        .addWarning(SyncBundle.message("maven.sync.annotation.processor.problem"), message);
    }

    if (problem.getMavenArtifact() != null) {
      syncConsole.showArtifactBuildIssue(MavenServerConsoleIndicator.ResolveType.DEPENDENCY,
                                         problem.getMavenArtifact().getMavenId().getKey(), message);
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

  public static class MavenResolveProblemHolder {
    @NotNull
    public final Set<MavenProjectProblem> repositoryBlockedProblems;
    @NotNull
    public final Set<MavenProjectProblem> unresolvedArtifactProblems;
    @NotNull
    public final Set<MavenArtifact> unresolvedArtifacts;

    public MavenResolveProblemHolder(@NotNull Set<MavenProjectProblem> repositoryBlockedProblems,
                                     @NotNull Set<MavenProjectProblem> unresolvedArtifactProblems,
                                     @NotNull Set<MavenArtifact> unresolvedArtifacts) {
      this.repositoryBlockedProblems = repositoryBlockedProblems;
      this.unresolvedArtifactProblems = unresolvedArtifactProblems;
      this.unresolvedArtifacts = unresolvedArtifacts;
    }

    public boolean isEmpty() {
      return repositoryBlockedProblems.isEmpty()
             && unresolvedArtifactProblems.isEmpty() && unresolvedArtifacts.isEmpty();
    }
  }
}
