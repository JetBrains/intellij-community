// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;

public final class JpsMavenDependenciesEnumerationHandler extends JpsJavaDependenciesEnumerationHandler {
  private static final JpsMavenDependenciesEnumerationHandler INSTANCE = new JpsMavenDependenciesEnumerationHandler();

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return true;
  }

  @Override
  public boolean isProductionOnTestsDependency(JpsDependencyElement element) {
    return JpsMavenExtensionService.getInstance().isProductionOnTestDependency(element);
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return false;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return false;
  }

  public static class MavenFactory extends Factory {
    @Override
    public @Nullable JpsJavaDependenciesEnumerationHandler createHandler(@NotNull Collection<JpsModule> modules) {
      JpsMavenExtensionService service = JpsMavenExtensionService.getInstance();
      for (JpsModule module : modules) {
        if (service.getExtension(module) != null) {
          return INSTANCE;
        }
      }
      return null;
    }
  }
}
