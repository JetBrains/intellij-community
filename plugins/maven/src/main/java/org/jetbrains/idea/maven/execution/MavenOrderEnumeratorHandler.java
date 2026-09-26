// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenUtil;

public class MavenOrderEnumeratorHandler extends OrderEnumerationHandler {

  public static final class FactoryImpl extends OrderEnumerationHandler.Factory {
    @Override
    public boolean isApplicable(@NotNull Module module) {
      return MavenUtil.isMavenizedModule(module);
    }

    @Override
    public @NotNull OrderEnumerationHandler createHandler(@NotNull Module module) {
      return INSTANCE;
    }
  }

  private static final MavenOrderEnumeratorHandler INSTANCE = new MavenOrderEnumeratorHandler();

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return true;
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return false;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return false;
  }

  @Override
  public boolean areResourceFilesFromSourceRootsCopiedToOutput() {
    return false;
  }
}
