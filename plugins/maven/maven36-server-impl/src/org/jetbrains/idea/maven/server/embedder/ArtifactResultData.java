// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.util.List;

class ArtifactResultData {
  private final Artifact artifact;
  private final ArtifactRepository repository;
  private final List<Exception> exceptions;
  private final ArtifactResolutionException resolutionException;

  ArtifactResultData(ArtifactResult result, ArtifactResolutionException resolutionException) {
    this(result.getArtifact(), result.getRepository(), result.getExceptions(), resolutionException);
  }

  ArtifactResultData(Artifact artifact,
                     ArtifactRepository repository,
                     List<Exception> exceptions,
                     ArtifactResolutionException resolutionException) {
    this.artifact = artifact;
    this.repository = repository;
    this.exceptions = exceptions;
    this.resolutionException = resolutionException;
  }

  Artifact getArtifact() {
    return artifact;
  }

  ArtifactRepository getRepository() {
    return repository;
  }

  List<Exception> getExceptions() {
    return exceptions;
  }

  ArtifactResolutionException getResolutionException() {
    return resolutionException;
  }

  @Override
  public String toString() {
    return "ArtifactResultData{" +
           "groupId='" + artifact.getGroupId() + '\'' +
           ", artifactId='" + artifact.getArtifactId() + '\'' +
           ", version='" + artifact.getVersion() + '\'' +
           ", repositoryId=" + repository.getId() +
           '}';
  }
}
