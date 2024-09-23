// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PluginResolutionRequest implements Serializable {
  private final @NotNull MavenId myMavenPluginId;
  private final int nativeMavenProjectId;
  private final boolean resolvePluginDependencies;
  private final @NotNull List<@NotNull MavenId> pluginDependencies;

  public PluginResolutionRequest(@NotNull MavenId mavenPluginId,
                                 int nativeMavenProjectId,
                                 boolean resolvePluginDependencies,
                                 @NotNull List<@NotNull MavenId> pluginDependencies
                                 ) {
    myMavenPluginId = mavenPluginId;
    this.nativeMavenProjectId = nativeMavenProjectId;
    this.resolvePluginDependencies = resolvePluginDependencies;
    this.pluginDependencies = new ArrayList<>(pluginDependencies);
  }

  @NotNull
  public MavenId getMavenPluginId() {
    return myMavenPluginId;
  }

  public int getNativeMavenProjectId() {
    return nativeMavenProjectId;
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
    return nativeMavenProjectId == request.nativeMavenProjectId &&
           resolvePluginDependencies == request.resolvePluginDependencies &&
           Objects.equals(myMavenPluginId, request.myMavenPluginId) &&
           Objects.equals(pluginDependencies, request.pluginDependencies);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMavenPluginId, nativeMavenProjectId, resolvePluginDependencies, pluginDependencies);
  }

  @Override
  public String toString() {
    return "PluginResolutionRequest{" +
           "pluginId=" + myMavenPluginId +
           ", resolveDependencies=" + resolvePluginDependencies +
           ", pluginDependencies=" + pluginDependencies +
           ", nativeMavenProjectId=" + nativeMavenProjectId +
           '}';
  }
}
