// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

public class MavenFolderResolver {
  public void resolveFolders(@NotNull Collection<MavenProject> mavenProjects,
                             @NotNull MavenProjectsTree tree,
                             @NotNull MavenImportingSettings importingSettings,
                             @NotNull MavenEmbeddersManager embeddersManager,
                             @NotNull MavenConsole console,
                             @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    var mavenProjectsToResolve = collectMavenProjectsToResolve(mavenProjects, tree);
    doResolveFolders(mavenProjectsToResolve, tree, importingSettings, embeddersManager, console, process);
  }

  @NotNull
  private Collection<MavenProject> collectMavenProjectsToResolve(@NotNull Collection<MavenProject> mavenProjects,
                                                                 @NotNull MavenProjectsTree tree) {
    // if we generate sources for the aggregator of a project, sources will be generated for the project too
    if (Registry.is("maven.server.generate.sources.for.aggregator.projects")) {
      return tree.collectAggregators(mavenProjects);
    }
    return mavenProjects;
  }

  private void doResolveFolders(@NotNull Collection<MavenProject> mavenProjects,
                                @NotNull MavenProjectsTree tree,
                                @NotNull MavenImportingSettings importingSettings,
                                @NotNull MavenEmbeddersManager embeddersManager,
                                @NotNull MavenConsole console,
                                @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    var projectMultiMap = MavenUtil.groupByBasedir(mavenProjects, tree);
    for (var entry : projectMultiMap.entrySet()) {
      var baseDir = entry.getKey();
      var mavenProjectsForBaseDir = entry.getValue();
      resolveFolders(baseDir,
                     mavenProjectsForBaseDir,
                     tree,
                     importingSettings,
                     embeddersManager,
                     console,
                     process);
    }
  }

  private void resolveFolders(@NotNull String baseDir,
                              @NotNull Collection<MavenProject> mavenProjects,
                              @NotNull MavenProjectsTree tree,
                              @NotNull MavenImportingSettings importingSettings,
                              @NotNull MavenEmbeddersManager embeddersManager,
                              @NotNull MavenConsole console,
                              @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    var goal = importingSettings.getUpdateFoldersOnImportPhase();

    var task = new MavenEmbeddersManager.EmbedderTask() {
      @Override
      public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
        process.checkCanceled();
        process.setText(MavenProjectBundle.message("maven.updating.folders"));
        process.setText2("");

        var fileToProject = mavenProjects.stream()
          .collect(Collectors.toMap(mavenProject -> new File(mavenProject.getFile().getPath()), mavenProject -> mavenProject));

        var requests = ContainerUtil.map(
          fileToProject.entrySet(),
          entry -> new MavenGoalExecutionRequest(entry.getKey(), entry.getValue().getActivatedProfilesIds())
        );

        var results = embedder.executeGoal(requests, goal, process);

        for (var result : results) {
          var mavenProject = fileToProject.getOrDefault(result.file, null);
          if (null != mavenProject && MavenUtil.shouldResetDependenciesAndFolders(result.problems)) {
            var changes = mavenProject.setFolders(result.folders);
            tree.fireFoldersResolved(Pair.create(mavenProject, changes));
          }
        }
      }
    };

    embeddersManager.execute(
      baseDir,
      MavenEmbeddersManager.FOR_FOLDERS_RESOLVE,
      console,
      process,
      task
    );
  }
}
