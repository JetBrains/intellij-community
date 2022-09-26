// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven2;

import com.intellij.ide.plugins.DependencyCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenVersionSupportUtil;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collections;
import java.util.List;

public class Maven2VersionDependencyCollector implements DependencyCollector {
  @NotNull
  @Override
  public List<String> collectDependencies(@NotNull Project project) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) return Collections.emptyList();
    if (MavenVersionSupportUtil.isMaven2Used(project)) return Collections.singletonList("maven2");
    return Collections.emptyList();
  }
}
