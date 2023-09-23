// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class MavenProjectRootWatcher implements ModuleRootListener {

  private final @NotNull MavenProjectsManager myProjectManager;
  private final @NotNull MavenProjectManagerWatcher myWatcher;

  public MavenProjectRootWatcher(@NotNull MavenProjectsManager projectManager, @NotNull MavenProjectManagerWatcher watcher) {
    myProjectManager = projectManager;
    myWatcher = watcher;
  }

  @Override
  public void rootsChanged(@NotNull ModuleRootEvent event) {
    // todo is this logic necessary?
    List<VirtualFile> existingFiles = myWatcher.getProjectTree().getProjectsFiles();
    List<VirtualFile> newFiles = new ArrayList<>();
    List<VirtualFile> deletedFiles = new ArrayList<>();

    for (VirtualFile f : myWatcher.getProjectTree().getExistingManagedFiles()) {
      if (!existingFiles.contains(f)) {
        newFiles.add(f);
      }
    }

    for (VirtualFile f : existingFiles) {
      if (!f.isValid()) deletedFiles.add(f);
    }

    if (!deletedFiles.isEmpty() || !newFiles.isEmpty()) {
      myProjectManager.scheduleUpdateMavenProjects(new MavenImportSpec(false, false, true), newFiles, deletedFiles);
    }
  }
}
