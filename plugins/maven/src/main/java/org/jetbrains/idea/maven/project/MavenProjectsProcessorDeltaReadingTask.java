// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.List;

public class MavenProjectsProcessorDeltaReadingTask implements MavenProjectsProcessorTask {
  private final boolean myForce;
  private final MavenProjectsTree myTree;
  private final MavenGeneralSettings mySettings;
  private final List<VirtualFile> myFilesToUpdate;
  private final List<VirtualFile> myFilesToDelete;
  @Nullable private final Runnable myOnCompletion;

  public MavenProjectsProcessorDeltaReadingTask(@NotNull List<VirtualFile> filesToUpdate,
                                                @NotNull List<VirtualFile> filesToDelete,
                                                boolean force,
                                                MavenProjectsTree tree,
                                                MavenGeneralSettings settings,
                                                @Nullable Runnable onCompletion) {
    myForce = force;
    myTree = tree;
    mySettings = settings;
    myFilesToUpdate = filesToUpdate;
    myFilesToDelete = filesToDelete;
    myOnCompletion = onCompletion;
  }

  @Override
  public void perform(Project project,
                      MavenEmbeddersManager embeddersManager,
                      MavenConsole console,
                      MavenProgressIndicator indicator) throws MavenProcessCanceledException {
    try {
      myTree.delete(myFilesToDelete, mySettings, indicator);
      myTree.update(myFilesToUpdate, myForce, mySettings, indicator);

      mySettings.updateFromMavenConfig(myTree.getRootProjectsFiles());
    }
    finally {
      if (myOnCompletion != null) myOnCompletion.run();
    }
  }
}
