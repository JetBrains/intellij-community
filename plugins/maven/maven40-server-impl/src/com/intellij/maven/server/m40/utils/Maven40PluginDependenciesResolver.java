// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.internal.DefaultPluginDependenciesResolver;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.sisu.Priority;
import org.jetbrains.idea.maven.server.MavenServerGlobals;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

@Named
@Singleton
@Priority(10)
public class Maven40PluginDependenciesResolver implements PluginDependenciesResolver {

  private final PluginDependenciesResolver delegate;

  @Inject
  public Maven40PluginDependenciesResolver(DefaultPluginDependenciesResolver delegate) {
    this.delegate = delegate;
  }

  @Override
  public Artifact resolve(Plugin plugin, List<RemoteRepository> repositories, RepositorySystemSession session)
    throws PluginResolutionException {
    return delegate.resolve(plugin, repositories, session);
  }

  @SuppressWarnings("deprecation")
  @Override
  public DependencyNode resolve(Plugin plugin, Artifact pluginArtifact, DependencyFilter dependencyFilter,
                                List<RemoteRepository> repositories, RepositorySystemSession session)
    throws PluginResolutionException {
    return retryResolution(
      () -> delegate.resolve(plugin, pluginArtifact, dependencyFilter, repositories, session)
    );
  }

  @Override
  public DependencyResult resolvePlugin(Plugin plugin, Artifact artifact, DependencyFilter dependencyFilter,
                                        List<RemoteRepository> remotePluginRepositories,
                                        RepositorySystemSession repositorySession)
    throws PluginResolutionException {
    //noinspection deprecation
    return retryResolution(
      () -> delegate.resolvePlugin(plugin, artifact, dependencyFilter, remotePluginRepositories, repositorySession)
    );
  }


  @Override
  public DependencyResult resolvePluginAndFlatten(
    Plugin plugin,
    Artifact pluginArtifact,
    DependencyFilter dependencyFilter,
    List<RemoteRepository> repositories,
    RepositorySystemSession session)
    throws PluginResolutionException {
    return retryResolution(
      () -> delegate.resolvePluginAndFlatten(plugin, pluginArtifact, dependencyFilter, repositories, session)
    );
  }

  @Override
  public DependencyResult resolveCoreExtensionAndFlatten(
    Plugin plugin,
    DependencyFilter dependencyFilter,
    List<RemoteRepository> repositories,
    RepositorySystemSession session)
    throws PluginResolutionException {
    return retryResolution(
      () -> delegate.resolveCoreExtensionAndFlatten(plugin, dependencyFilter, repositories, session)
    );
  }

  private static <T> T retryResolution(ResolverAction<T> action) throws PluginResolutionException {
    try {
      return action.resolve();
    }
    catch (PluginResolutionException firstException) {
      MavenServerGlobals.getLogger().warn("Exception during plugin resolution. Will retry", firstException);
      return action.resolve();
    }
  }

  @FunctionalInterface
  private interface ResolverAction<T> {
    T resolve() throws PluginResolutionException;
  }
}