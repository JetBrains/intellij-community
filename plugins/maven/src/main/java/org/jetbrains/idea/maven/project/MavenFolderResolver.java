// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;
import java.util.List;

public class MavenFolderResolver {
  public void resolveFolders(@NotNull Collection<MavenProject> mavenProjects,
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
    var task = new MavenEmbeddersManager.EmbedderTask() {
      @Override
      public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
        for (var mavenProject : mavenProjects) {
          process.checkCanceled();
          process.setText(MavenProjectBundle.message("maven.updating.folders.pom", mavenProject.getDisplayName()));
          process.setText2("");

          var file = mavenProject.getFile();
          var profiles = mavenProject.getActivatedProfilesIds();

          try {
            var goals = List.of(importingSettings.getUpdateFoldersOnImportPhase());

            var result = embedder.execute(file, profiles.getEnabledProfiles(), profiles.getDisabledProfiles(), goals);

            var projectData = result.projectData;
            if (projectData == null) continue;

            var readerResult = new MavenProjectReaderResult(
              projectData.mavenModel,
              projectData.mavenModelMap,
              new MavenExplicitProfiles(projectData.activatedProfiles, profiles.getDisabledProfiles()),
              projectData.nativeMavenProject,
              result.problems,
              result.unresolvedArtifacts);

            if (MavenProjectReaderResult.shouldResetDependenciesAndFolders(readerResult)) {
              MavenProjectChanges changes = mavenProject.setFolders(readerResult);
              tree.fireFoldersResolved(Pair.create(mavenProject, changes));
            }
          }
          catch (Throwable e) {
            console.printException(e);
            MavenLog.LOG.warn(e);
          }
        }
      }
    };

    embeddersManager.execute(
      baseDir,
      tree,
      MavenEmbeddersManager.FOR_FOLDERS_RESOLVE,
      console,
      process,
      task
    );
  }
}
