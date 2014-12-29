package org.jetbrains.jps.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsMavenExtensionService {
  public static JpsMavenExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsMavenExtensionService.class);
  }

  @Nullable
  public abstract JpsMavenModuleExtension getExtension(@NotNull JpsModule module);

  @NotNull
  public abstract JpsMavenModuleExtension getOrCreateExtension(@NotNull JpsModule module);

  public abstract void setProductionOnTestDependency(@NotNull JpsDependencyElement dependency, boolean value);

  public abstract boolean isProductionOnTestDependency(@NotNull JpsDependencyElement dependency);

  public abstract boolean hasMavenProjectConfiguration(@NotNull BuildDataPaths paths);

  @Nullable
  public abstract MavenProjectConfiguration getMavenProjectConfiguration(BuildDataPaths paths);
}
