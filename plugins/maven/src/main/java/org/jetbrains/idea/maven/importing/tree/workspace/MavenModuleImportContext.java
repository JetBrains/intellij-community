// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenModuleImportContext {
  @NotNull final List<MavenModuleImportData> changedModules;
  @NotNull final List<MavenModuleImportData> allModules;
  @NotNull final Map<MavenProject, String> moduleNameByProject;
  final boolean hasChanges;

  public MavenModuleImportContext() {
    this.changedModules = Collections.emptyList();
    this.allModules = Collections.emptyList();
    this.moduleNameByProject = Collections.emptyMap();
    this.hasChanges = false;
  }

  public MavenModuleImportContext(@NotNull List<MavenModuleImportData> changedModules,
                                  @NotNull List<MavenModuleImportData> allModules,
                                  @NotNull Map<MavenProject, String> moduleNameByProject,
                                  boolean hasChanges) {
    this.changedModules = changedModules;
    this.allModules = allModules;
    this.moduleNameByProject = moduleNameByProject;
    this.hasChanges = hasChanges;
  }
}
