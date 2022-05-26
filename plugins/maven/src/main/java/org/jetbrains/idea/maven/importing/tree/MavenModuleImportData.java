// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;

import java.util.List;

public class MavenModuleImportData {
  @NotNull private final MavenProject mavenProject;
  @NotNull private final ModuleData moduleData;
  @NotNull private final List<MavenImportDependency<?>> dependencies;
  @Nullable private final MavenProjectChanges changes;

  public MavenModuleImportData(@NotNull MavenProject mavenProject,
                               @NotNull ModuleData moduleData,
                               @NotNull List<MavenImportDependency<?>> dependencies,
                               @Nullable MavenProjectChanges changes) {
    this.mavenProject = mavenProject;
    this.moduleData = moduleData;
    this.dependencies = dependencies;
    this.changes = changes;
  }

  public @NotNull MavenProject getMavenProject() {
    return mavenProject;
  }

  public @Nullable MavenProjectChanges getChanges() {
    return changes;
  }

  public @NotNull ModuleData getModuleData() {
    return moduleData;
  }

  public boolean isNewModule() {
    return moduleData.isNewModule();
  }

  public @NotNull List<MavenImportDependency<?>> getDependencies() {
    return dependencies;
  }

  public boolean hasChanges() {
    return changes != null && changes.hasChanges();
  }

  @Override
  public String toString() {
    return moduleData.getModuleName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenModuleImportData data = (MavenModuleImportData)o;

    if (!mavenProject.equals(data.mavenProject)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return mavenProject.hashCode();
  }
}
