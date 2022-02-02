// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;

public class ModuleDependency extends MavenImportDependency<String> {

  @Nullable private final LibraryDependency libraryDependency;
  private final boolean testJar;

  public ModuleDependency(@Nullable MavenArtifact artifact,
                          @NotNull MavenProject project,
                          @NotNull String moduleName,
                          @NotNull DependencyScope scope,
                          boolean testJar) {
    super(moduleName, scope);
    this.libraryDependency = artifact == null ? null : new LibraryDependency(artifact, project, scope);
    this.testJar = testJar;
  }

  public @Nullable LibraryDependency getLibraryDependency() {
    return libraryDependency;
  }

  public boolean isTestJar() {
    return testJar;
  }
}
