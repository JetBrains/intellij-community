// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.util.Collection;
import java.util.Map;

public interface MavenProjectResolver {
  MavenProjectResolutionResult resolve(@NotNull Collection<MavenProject> mavenProjects,
                                       @NotNull MavenProjectsTree tree,
                                       @NotNull MavenGeneralSettings generalSettings,
                                       @NotNull MavenEmbeddersManager embeddersManager,
                                       @NotNull MavenConsole console,
                                       @NotNull ProgressIndicator process,
                                       @Nullable MavenSyncConsole syncConsole) throws MavenProcessCanceledException;

  static MavenProjectResolver getInstance(@NotNull Project project) {
    return project.getService(MavenProjectResolver.class);
  }

  record MavenProjectResolutionResult(@NotNull Map<String, Collection<MavenProjectWithHolder>> mavenProjectMap) {
  }
}
