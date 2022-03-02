// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;

public class SystemDependency extends MavenImportDependency<MavenArtifact> {

  public SystemDependency(@NotNull MavenArtifact artifact,
                          @NotNull DependencyScope scope) {
    super(artifact, scope);
  }
}
