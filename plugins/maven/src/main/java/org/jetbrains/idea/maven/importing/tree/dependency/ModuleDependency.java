// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDependency extends MavenImportDependency<String> {
  @Nullable private final LibraryDependency libraryDependency;
  @Nullable private final AttachedJarDependency attachedJarDependency;
  private final boolean testJar;

  public ModuleDependency(@NotNull String moduleName,
                          @Nullable LibraryDependency libraryDependency,
                          @Nullable AttachedJarDependency attachedJarDependency,
                          @NotNull DependencyScope scope,
                          boolean testJar) {
    super(moduleName, scope);
    this.libraryDependency = libraryDependency;
    this.attachedJarDependency = attachedJarDependency;
    this.testJar = testJar;
  }

  public @Nullable LibraryDependency getLibraryDependency() {
    return libraryDependency;
  }

  public @Nullable AttachedJarDependency getAttachedJarDependency() {
    return attachedJarDependency;
  }

  public boolean isTestJar() {
    return testJar;
  }
}
