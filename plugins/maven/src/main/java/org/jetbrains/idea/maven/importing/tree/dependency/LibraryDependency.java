// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;

public class LibraryDependency extends MavenImportDependency<MavenArtifact> {

  @NotNull private final MavenProject project;

  public LibraryDependency(@NotNull MavenArtifact artifact,
                           @NotNull MavenProject project,
                           @NotNull DependencyScope scope) {
    super(artifact, scope);
    this.project = project;
  }
}
