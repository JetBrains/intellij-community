// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PluginResolutionRequest implements Serializable {
  private final @NotNull MavenId myMavenPluginId;
  @NotNull List<@NotNull MavenRemoteRepository> repositories;
  private final boolean resolvePluginDependencies;
  private final @NotNull List<@NotNull MavenId> pluginDependencies;

  public PluginResolutionRequest(@NotNull MavenId mavenPluginId,
                                 @NotNull List<@NotNull MavenRemoteRepository> repositories,
                                 boolean resolvePluginDependencies,
                                 @NotNull List<@NotNull MavenId> pluginDependencies
                                 ) {
    myMavenPluginId = mavenPluginId;
    this.repositories = new ArrayList<>(repositories);
    this.resolvePluginDependencies = resolvePluginDependencies;
    this.pluginDependencies = new ArrayList<>(pluginDependencies);
  }

  public @NotNull MavenId getMavenPluginId() {
    return myMavenPluginId;
  }

  public @NotNull List<@NotNull MavenRemoteRepository> getRepositories() {
    return repositories;
  }

  public boolean resolvePluginDependencies() {
    return resolvePluginDependencies;
  }

  public @NotNull List<@NotNull MavenId> getPluginDependencies() {
    return pluginDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PluginResolutionRequest request = (PluginResolutionRequest)o;
    return resolvePluginDependencies == request.resolvePluginDependencies &&
           Objects.equals(myMavenPluginId, request.myMavenPluginId) &&
           Objects.equals(repositories, request.repositories) &&
           Objects.equals(pluginDependencies, request.pluginDependencies);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMavenPluginId, repositories, resolvePluginDependencies, pluginDependencies);
  }

  @Override
  public String toString() {
    return "PluginResolutionRequest{" +
           "pluginId=" + myMavenPluginId +
           ", resolveDependencies=" + resolvePluginDependencies +
           ", pluginDependencies=" + pluginDependencies +
           ", repositories=" + repositories +
           '}';
  }
}
