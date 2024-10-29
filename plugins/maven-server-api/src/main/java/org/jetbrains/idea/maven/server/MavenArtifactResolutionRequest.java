// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public final class MavenArtifactResolutionRequest implements Serializable {
  @NotNull
  private final MavenArtifactInfo myArtifactInfo;
  @NotNull
  private final List<MavenRemoteRepository> myRemoteRepositories;
  private boolean myUpdateSnapshots;

  public MavenArtifactResolutionRequest(@NotNull MavenArtifactInfo mavenArtifactInfo,
                                        @NotNull List<MavenRemoteRepository> repositories,
                                        boolean updateSnapshots) {
    myArtifactInfo = mavenArtifactInfo;
    myRemoteRepositories = repositories;
    myUpdateSnapshots = updateSnapshots;
  }

  public MavenArtifactResolutionRequest(@NotNull MavenArtifactInfo mavenArtifactInfo,
                                        @NotNull List<MavenRemoteRepository> repositories) {
    this(mavenArtifactInfo, repositories, false);
  }

  @NotNull
  public MavenArtifactInfo getArtifactInfo() {
    return myArtifactInfo;
  }

  @NotNull
  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  public boolean updateSnapshots() {
    return myUpdateSnapshots;
  }

  public void setUpdateSnapshots(boolean updateSnapshots) {
    this.myUpdateSnapshots = updateSnapshots;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MavenArtifactResolutionRequest request = (MavenArtifactResolutionRequest)o;
    return myUpdateSnapshots == request.myUpdateSnapshots &&
           Objects.equals(myArtifactInfo, request.myArtifactInfo) &&
           Objects.equals(myRemoteRepositories, request.myRemoteRepositories);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myArtifactInfo, myRemoteRepositories, myUpdateSnapshots);
  }

  @Override
  public String toString() {
    return "MavenArtifactResolutionRequest{" +
           "myArtifactInfo=" + myArtifactInfo +
           ", myRemoteRepositories=" + myRemoteRepositories +
           ", myUpdateSnapshots=" + myUpdateSnapshots +
           '}';
  }
}
