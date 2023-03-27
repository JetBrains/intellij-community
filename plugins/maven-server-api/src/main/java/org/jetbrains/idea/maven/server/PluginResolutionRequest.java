// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenPlugin;

import java.io.Serializable;
import java.util.Objects;

public class PluginResolutionRequest implements Serializable {
  private final @NotNull MavenPlugin myMavenPlugin;
  private final int nativeMavenProjectId;

  public PluginResolutionRequest(@NotNull MavenPlugin mavenPlugin, int nativeMavenProjectId) {
    myMavenPlugin = mavenPlugin;
    this.nativeMavenProjectId = nativeMavenProjectId;
  }

  @NotNull
  public MavenPlugin getMavenPlugin() {
    return myMavenPlugin;
  }

  public int getNativeMavenProjectId() {
    return nativeMavenProjectId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PluginResolutionRequest request = (PluginResolutionRequest)o;
    return nativeMavenProjectId == request.nativeMavenProjectId && myMavenPlugin.equals(request.myMavenPlugin);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMavenPlugin, nativeMavenProjectId);
  }
}
