// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsManagerEx;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import javax.swing.*;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

public final class MavenIconProvider implements DumbAware, FileIconProvider {
  @Override
  public @Nullable Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
    if (project == null) return null;
    MavenProjectsManagerEx manager = (MavenProjectsManagerEx)MavenProjectsManager.getInstanceIfCreated(project);
    if (manager == null || !manager.isMavenizedProject()) return null;

    MavenProjectsTree tree = manager.getProjectsTreeIfInitialized();
    if (tree == null) return null;
    MavenProject mavenProject = tree.findProject(file);
    if (mavenProject != null) {
      if (tree.isIgnored(mavenProject)) {
        return MavenIcons.MavenIgnored;
      }
      return RepositoryLibraryLogo;
    }

    return null;
  }
}
