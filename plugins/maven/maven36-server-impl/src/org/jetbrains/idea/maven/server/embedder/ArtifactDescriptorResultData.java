// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class ArtifactDescriptorResultData {
  private final Artifact artifact;
  private final ArtifactRepository repository;
  private final List<Exception> exceptions;
  private final List<Artifact> relocations;
  private final Collection<Artifact> aliases;
  private final List<Dependency> dependencies;
  private final List<Dependency> managedDependencies;
  private final List<RemoteRepository> repositories;
  private final Map<String, Object> properties;
  private final ArtifactDescriptorException descriptorException;

  ArtifactDescriptorResultData(Artifact artifact,
                               ArtifactRepository repository,
                               List<Exception> exceptions,
                               List<Artifact> relocations,
                               Collection<Artifact> aliases,
                               List<Dependency> dependencies,
                               List<Dependency> managedDependencies,
                               List<RemoteRepository> repositories,
                               Map<String, Object> properties,
                               ArtifactDescriptorException descriptorException) {
    this.artifact = artifact;
    this.repository = repository;
    this.exceptions = exceptions;
    this.relocations = relocations;
    this.aliases = aliases;
    this.dependencies = dependencies;
    this.managedDependencies = managedDependencies;
    this.repositories = repositories;
    this.properties = properties;
    this.descriptorException = descriptorException;
  }

  ArtifactDescriptorResultData(ArtifactDescriptorResult result, ArtifactDescriptorException descriptorException) {
    this(result.getArtifact(),
         result.getRepository(),
         result.getExceptions(),
         result.getRelocations(),
         result.getAliases(),
         result.getDependencies(),
         result.getManagedDependencies(),
         result.getRepositories(),
         result.getProperties(),
         descriptorException);
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

  ArtifactDescriptorException getDescriptorException() {
    return descriptorException;
  }

  List<Artifact> getRelocations() {
    return relocations;
  }

  Collection<Artifact> getAliases() {
    return aliases;
  }

  List<Dependency> getDependencies() {
    return dependencies;
  }

  List<Dependency> getManagedDependencies() {
    return managedDependencies;
  }

  List<RemoteRepository> getRepositories() {
    return repositories;
  }

  Map<String, Object> getProperties() {
    return properties;
  }

  @Override
  public String toString() {
    return "ArtifactDescriptorResultData{" +
           "groupId='" + artifact.getGroupId() + '\'' +
           ", artifactId='" + artifact.getArtifactId() + '\'' +
           ", version='" + artifact.getVersion() + '\'' +
           ", repositoryId=" + repository.getId() +
           '}';
  }
}
