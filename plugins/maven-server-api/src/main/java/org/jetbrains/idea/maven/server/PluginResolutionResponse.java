// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class PluginResolutionResponse implements Serializable {
  private final @NotNull MavenId myMavenPluginId;
  private final boolean myResolved;
  private final @NotNull List<MavenArtifact> myArtifacts;

  public PluginResolutionResponse(@NotNull MavenId mavenPluginId, boolean resolved, @NotNull List<MavenArtifact> artifacts) {
    myMavenPluginId = mavenPluginId;
    myResolved = resolved;
    myArtifacts = artifacts;
  }

  @NotNull
  public MavenId getMavenPluginId() {
    return myMavenPluginId;
  }

  public boolean isResolved() {
    return myResolved;
  }

  @NotNull
  public List<MavenArtifact> getArtifacts() {
    return myArtifacts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PluginResolutionResponse response = (PluginResolutionResponse)o;
    return myResolved == response.myResolved &&
           myMavenPluginId.equals(response.myMavenPluginId) &&
           myArtifacts.equals(response.myArtifacts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMavenPluginId, myResolved, myArtifacts);
  }

  @Override
  public String toString() {
    return "PluginResolutionResponse{" +
           "pluginId=" + myMavenPluginId +
           ", resolved=" + myResolved +
           ", artifacts=" + myArtifacts +
           '}';
  }
}
