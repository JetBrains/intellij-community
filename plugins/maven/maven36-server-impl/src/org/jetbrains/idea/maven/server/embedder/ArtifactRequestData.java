// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.resolution.ArtifactRequest;

import java.util.Objects;

class ArtifactRequestData {
  private final ArtifactData artifactData;
  private final RepositoryDataList repositories;

  ArtifactRequestData(ArtifactData artifactData, RepositoryDataList repositories) {
    this.artifactData = artifactData;
    this.repositories = repositories;
  }

  ArtifactRequestData(ArtifactRequest request) {
    this(new ArtifactData(request.getArtifact()), new RepositoryDataList(request.getRepositories()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArtifactRequestData data = (ArtifactRequestData)o;

    if (!Objects.equals(artifactData, data.artifactData)) return false;
    if (!Objects.equals(repositories, data.repositories)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = artifactData != null ? artifactData.hashCode() : 0;
    result = 31 * result + (repositories != null ? repositories.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ArtifactRequestData{" +
           "artifactData='" + artifactData + '\'' +
           '}';
  }
}
