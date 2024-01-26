// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Collection;
import java.util.function.Predicate;

public interface MavenServerManager extends Disposable {

  Collection<MavenServerConnector> getAllConnectors();

  void restartMavenConnectors(Project project, boolean wait, Predicate<MavenServerConnector> condition);

  MavenServerConnector getConnector(@NotNull Project project, @NotNull String workingDirectory);

  boolean shutdownConnector(MavenServerConnector connector, boolean wait);

  @TestOnly
  void closeAllConnectorsAndWait();

  File getMavenEventListener();

  /**
   * @deprecated use {@link MavenServerManager#createEmbedder(Project, boolean, String)}
   */
  @Deprecated
  @NotNull
  default MavenEmbedderWrapper createEmbedder(Project project,
                                              boolean alwaysOnline,
                                              @Nullable String ignoredWorkingDirectory,
                                              @NotNull String multiModuleProjectDirectory) {
    return createEmbedder(project, alwaysOnline, multiModuleProjectDirectory);
  }

  @NotNull
  MavenEmbedderWrapper createEmbedder(Project project,
                                      boolean alwaysOnline,
                                      @NotNull String multiModuleProjectDirectory);

  /**
   * @deprecated use createIndexer()
   */
  @Deprecated
  MavenIndexerWrapper createIndexer(@NotNull Project project);

  MavenIndexerWrapper createIndexer();

  static MavenServerManager getInstance() {
    return ApplicationManager.getApplication().getService(MavenServerManager.class);
  }

  @Nullable
  static MavenServerManager getInstanceIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(MavenServerManager.class);
  }

  /**
   * @deprecated use {@link MavenGeneralSettings.mavenHome} and {@link MavenUtil.getMavenVersion}
   */
  @Nullable
  @Deprecated(forRemoval = true)
  default String getCurrentMavenVersion() {
    return null;
  }

  default boolean isUseMaven2() {
    return false;
  }

  @ApiStatus.Internal
  interface MavenServerConnectorFactory {
    @NotNull
    MavenServerConnector create(@NotNull Project project,
                                @NotNull Sdk jdk,
                                @NotNull String vmOptions,
                                @Nullable Integer debugPort,
                                @NotNull MavenDistribution mavenDistribution,
                                @NotNull String multimoduleDirectory);
  }

  @ApiStatus.Internal
  class MavenServerConnectorFactoryImpl implements MavenServerConnectorFactory {

    @Override
    public @NotNull MavenServerConnector create(@NotNull Project project,
                                                @NotNull Sdk jdk,
                                                @NotNull String vmOptions,
                                                @Nullable Integer debugPort,
                                                @NotNull MavenDistribution mavenDistribution,
                                                @NotNull String multimoduleDirectory) {
      return new MavenServerConnectorImpl(project, jdk, vmOptions, debugPort, mavenDistribution, multimoduleDirectory);
    }
  }
}
