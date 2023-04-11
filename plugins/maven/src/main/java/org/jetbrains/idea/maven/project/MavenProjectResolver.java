// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;

public interface MavenProjectResolver {
  void resolve(@NotNull Collection<MavenProject> mavenProjects,
               @NotNull MavenGeneralSettings generalSettings,
               @NotNull MavenEmbeddersManager embeddersManager,
               @NotNull MavenConsole console,
               @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException;

  static MavenProjectResolver getInstance(@NotNull Project project) {
    return project.getService(MavenProjectResolver.class);
  }
}
