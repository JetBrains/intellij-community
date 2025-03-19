// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class JpsMavenExtensionService {
  public static JpsMavenExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsMavenExtensionService.class);
  }

  public abstract @Nullable JpsMavenModuleExtension getExtension(@NotNull JpsModule module);

  public abstract @NotNull JpsMavenModuleExtension getOrCreateExtension(@NotNull JpsModule module);

  public abstract void setProductionOnTestDependency(@NotNull JpsDependencyElement dependency, boolean value);

  public abstract boolean isProductionOnTestDependency(@NotNull JpsDependencyElement dependency);

  public abstract boolean hasMavenProjectConfiguration(@NotNull BuildDataPaths paths);

  public abstract @Nullable MavenProjectConfiguration getMavenProjectConfiguration(BuildDataPaths paths);
}
