// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

public abstract class MavenImportDependency<T> {
  @NotNull
  private final T artifact;
  @NotNull
  private final DependencyScope scope;

  public MavenImportDependency(@NotNull T artifact,
                               @NotNull DependencyScope scope) {
    this.artifact = artifact;
    this.scope = scope;
  }

  public @NotNull T getArtifact() {
    return artifact;
  }

  public @NotNull DependencyScope getScope() {
    return scope;
  }

  @Override
  public String toString() {
    return artifact.toString();
  }
}
