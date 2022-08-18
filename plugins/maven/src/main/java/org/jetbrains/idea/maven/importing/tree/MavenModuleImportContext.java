// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenModuleImportContext {
  @NotNull public final List<MavenTreeModuleImportData> changedModules;
  @NotNull public final List<MavenTreeModuleImportData> allModules;
  @NotNull public final Map<MavenProject, String> moduleNameByProject;
  public final boolean hasChanges;

  @NotNull public final List<Module> legacyCreatedModules;
  @NotNull public final List<Module> legacyObsoleteModules;

  public MavenModuleImportContext() {
    this.changedModules = Collections.emptyList();
    this.allModules = Collections.emptyList();
    this.moduleNameByProject = Collections.emptyMap();
    this.hasChanges = false;

    this.legacyCreatedModules = Collections.emptyList();
    this.legacyObsoleteModules = Collections.emptyList();
  }

  public MavenModuleImportContext(@NotNull List<MavenTreeModuleImportData> changedModules,
                                  @NotNull List<MavenTreeModuleImportData> allModules,
                                  @NotNull Map<MavenProject, String> moduleNameByProject,
                                  boolean hasChanges,

                                  @NotNull List<Module> legacyCreatedModules,
                                  @NotNull List<Module> legacyObsoleteModules) {
    this.changedModules = changedModules;
    this.allModules = allModules;
    this.moduleNameByProject = moduleNameByProject;
    this.hasChanges = hasChanges;

    this.legacyCreatedModules = legacyCreatedModules;
    this.legacyObsoleteModules = legacyObsoleteModules;
  }
}
