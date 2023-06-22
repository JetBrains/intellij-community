// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;

import java.io.Serializable;
import java.util.List;

public final class MavenArtifactResolutionRequest implements Serializable {
  @NotNull
  private final MavenArtifactInfo myArtifactInfo;
  @NotNull
  private final List<MavenRemoteRepository> myRemoteRepositories;

  public MavenArtifactResolutionRequest(@NotNull MavenArtifactInfo mavenArtifactInfo, @NotNull List<MavenRemoteRepository> repositories) {
    myArtifactInfo = mavenArtifactInfo;
    myRemoteRepositories = repositories;
  }

  @NotNull
  public MavenArtifactInfo getArtifactInfo() {
    return myArtifactInfo;
  }

  @NotNull
  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenArtifactResolutionRequest request = (MavenArtifactResolutionRequest)o;

    if (!myArtifactInfo.equals(request.myArtifactInfo)) return false;
    if (!myRemoteRepositories.equals(request.myRemoteRepositories)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myArtifactInfo.hashCode();
    result = 31 * result + myRemoteRepositories.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MavenArtifactResolutionRequest{" +
           "myArtifactInfo=" + myArtifactInfo +
           ", myRemoteRepositories=" + myRemoteRepositories +
           '}';
  }
}
