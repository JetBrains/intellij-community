// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class PluginResolutionResponse implements Serializable {
  private final @NotNull MavenId myMavenPluginId;
  private final @Nullable MavenArtifact myPluginArtifact;
  private final @NotNull List<MavenArtifact> myDependencyArtifacts;

  public PluginResolutionResponse(@NotNull MavenId mavenPluginId,
                                  @Nullable MavenArtifact pluginArtifact,
                                  @NotNull List<MavenArtifact> dependencyArtifacts) {
    myMavenPluginId = mavenPluginId;
    myPluginArtifact = pluginArtifact;
    myDependencyArtifacts = dependencyArtifacts;
  }

  public @NotNull MavenId getMavenPluginId() {
    return myMavenPluginId;
  }

  public @Nullable MavenArtifact getPluginArtifact() {
    return myPluginArtifact;
  }

  public boolean isResolved() {
    return myPluginArtifact != null;
  }

  public @NotNull List<MavenArtifact> getPluginDependencyArtifacts() {
    return myDependencyArtifacts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PluginResolutionResponse response = (PluginResolutionResponse)o;
    return Objects.equals(myMavenPluginId, response.myMavenPluginId) &&
           Objects.equals(myPluginArtifact, response.myPluginArtifact) &&
           Objects.equals(myDependencyArtifacts, response.myDependencyArtifacts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMavenPluginId, myPluginArtifact, myDependencyArtifacts);
  }

  @Override
  public String toString() {
    return "PluginResolutionResponse{" +
           "pluginId=" + myMavenPluginId +
           ", plugin=" + myPluginArtifact +
           ", dependencies=" + myDependencyArtifacts +
           '}';
  }
}
