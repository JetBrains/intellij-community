// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

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

        MavenProjectReaderResult result = MavenProjectReader.generateSources(embedder,
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
}
