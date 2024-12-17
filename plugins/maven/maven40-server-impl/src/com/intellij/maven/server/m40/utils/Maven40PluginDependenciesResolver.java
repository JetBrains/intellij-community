// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.internal.DefaultPluginDependenciesResolver;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.model.Plugin;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyResult;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

@Named
@Singleton
public class Maven40PluginDependenciesResolver implements PluginDependenciesResolver {
  private final PluginDependenciesResolver delegate;

  @Inject
  public Maven40PluginDependenciesResolver(List<PluginDependenciesResolver> allResolvers) {
    this.delegate = findDefaultResolver(allResolvers);
  }

  private static PluginDependenciesResolver findDefaultResolver(List<PluginDependenciesResolver> allResolvers) {
    for (PluginDependenciesResolver resolver : allResolvers) {
      if (resolver instanceof DefaultPluginDependenciesResolver) return resolver;
    }
    throw new RuntimeException("No default resolver found");
  }

  @Override
  public Artifact resolve(Plugin plugin, List<RemoteRepository> repositories, RepositorySystemSession session)
    throws PluginResolutionException {
    return delegate.resolve(plugin, repositories, session);
  }

  @Override
  public DependencyNode resolve(Plugin plugin, Artifact pluginArtifact, DependencyFilter dependencyFilter,
                                List<RemoteRepository> repositories, RepositorySystemSession session)
    throws PluginResolutionException {
    return delegate.resolve(plugin, pluginArtifact, dependencyFilter, repositories, session);
  }

  @Override
  public DependencyResult resolvePlugin(Plugin plugin, Artifact artifact, DependencyFilter dependencyFilter,
                                        List<RemoteRepository> remotePluginRepositories,
                                        RepositorySystemSession repositorySession)
    throws PluginResolutionException {
    return delegate.resolvePlugin(plugin, artifact, dependencyFilter, remotePluginRepositories, repositorySession);
  }
}