// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public final class MavenRehighlighter {
  public static void install(@NotNull Project project, @NotNull MavenProjectsManager projectsManager) {
    project.getService(MavenHighlightingUpdater.class).install(projectsManager);
  }

  public static void rehighlight(@NotNull Project project) {
    project.getService(MavenHighlightingUpdater.class).schedule(null);
  }
}
