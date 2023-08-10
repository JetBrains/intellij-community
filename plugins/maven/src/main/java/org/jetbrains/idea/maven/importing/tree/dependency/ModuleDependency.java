// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

public class ModuleDependency extends MavenImportDependency<String> {
  private final boolean testJar;

  public ModuleDependency(@NotNull String moduleName,
                          @NotNull DependencyScope scope,
                          boolean testJar) {
    super(moduleName, scope);
    this.testJar = testJar;
  }

  public boolean isTestJar() {
    return testJar;
  }
}
