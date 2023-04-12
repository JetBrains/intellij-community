// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collections;
import java.util.List;

public class MavenFolderResolver {
  public void resolveFolders(@NotNull MavenProject mavenProject,
                             @NotNull MavenProjectsTree tree,
                             @NotNull MavenImportingSettings importingSettings,
                             @NotNull MavenEmbeddersManager embeddersManager,
                             @NotNull MavenConsole console,
                             @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    var task = new MavenEmbeddersManager.EmbedderTask() {
      @Override
      public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
        process.checkCanceled();
        process.setText(MavenProjectBundle.message("maven.updating.folders.pom", mavenProject.getDisplayName()));
        process.setText2("");

        MavenProjectReaderResult result = generateSources(embedder,
                                                          importingSettings,
                                                          mavenProject.getFile(),
                                                          mavenProject.getActivatedProfilesIds(),
                                                          console);

        if (result != null && MavenProjectReaderResult.shouldResetDependenciesAndFolders(result)) {
          MavenProjectChanges changes = mavenProject.setFolders(result);
          tree.fireFoldersResolved(Pair.create(mavenProject, changes));
        }
      }
    };

    embeddersManager.execute(
      mavenProject,
      tree,
      MavenEmbeddersManager.FOR_FOLDERS_RESOLVE,
      console,
      process,
      task
    );
  }

  @Nullable
  private static MavenProjectReaderResult generateSources(MavenEmbedderWrapper embedder,
                                                          MavenImportingSettings importingSettings,
                                                          VirtualFile file,
                                                          MavenExplicitProfiles profiles,
                                                          MavenConsole console) {
    try {
      List<String> goals = Collections.singletonList(importingSettings.getUpdateFoldersOnImportPhase());
      MavenServerExecutionResult result = embedder.execute(file, profiles.getEnabledProfiles(), profiles.getDisabledProfiles(), goals);
      MavenServerExecutionResult.ProjectData projectData = result.projectData;
      if (projectData == null) return null;

      return new MavenProjectReaderResult(projectData.mavenModel,
                                          projectData.mavenModelMap,
                                          new MavenExplicitProfiles(projectData.activatedProfiles, profiles.getDisabledProfiles()),
                                          projectData.nativeMavenProject,
                                          result.problems,
                                          result.unresolvedArtifacts);
    }
    catch (Throwable e) {
      console.printException(e);
      MavenLog.LOG.warn(e);
      return null;
    }
  }
}
