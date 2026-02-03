// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public class MavenEmbedderSettings implements Serializable {
  private static final String DEFAULT_RESOLVER = "org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactResolver";
  private final @NotNull MavenServerSettings settings;
  private final @Nullable String multiModuleProjectDirectory;
  private final boolean forceResolveDependenciesSequentially;
  private final boolean useCustomDependenciesResolver;

  public MavenEmbedderSettings(@NotNull MavenServerSettings settings) {
    this(settings, null, false, true);
  }

  public MavenEmbedderSettings(@NotNull MavenServerSettings settings,
                               @Nullable String multiModuleProjectDirectory,
                               boolean forceResolveDependenciesSequentially,
                               boolean useCustomDependenciesResolver) {
    this.settings = settings;
    this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    this.forceResolveDependenciesSequentially = forceResolveDependenciesSequentially;
    this.useCustomDependenciesResolver = useCustomDependenciesResolver;
  }

  public @NotNull MavenServerSettings getSettings() {
    return settings;
  }

  public @Nullable String getMultiModuleProjectDirectory() {
    return multiModuleProjectDirectory;
  }

  public boolean forceResolveDependenciesSequentially() {
    return forceResolveDependenciesSequentially;
  }

  public boolean useCustomDependenciesResolver() {
    return useCustomDependenciesResolver;
  }
}
