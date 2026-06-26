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
  private static final MethodHandle RESOLVE_PLUGIN_AND_FLATTEN = findResolvePluginAndFlatten();

  private final PluginDependenciesResolver delegate;

  @Inject
  public Maven40PluginDependenciesResolver(DefaultPluginDependenciesResolver delegate) {
    this.delegate = delegate;
  }

  private static MethodHandle findResolvePluginAndFlatten() {
    try {
      MethodType type = MethodType.methodType(
        DependencyResult.class, Plugin.class, Artifact.class, DependencyFilter.class,
        List.class, RepositorySystemSession.class);
      //noinspection JavaLangInvokeHandleSignature - the method is absent in the Maven version we compile against, resolved at runtime
      return MethodHandles.lookup().findVirtual(PluginDependenciesResolver.class, "resolvePluginAndFlatten", type);
    }
    catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  private static PluginDependenciesResolver findDefaultResolver(List<PluginDependenciesResolver> allResolvers) {
    for (PluginDependenciesResolver resolver : allResolvers) {
      if (resolver instanceof DefaultPluginDependenciesResolver) return resolver;
    }
    throw new RuntimeException("DefaultPluginDependenciesResolver not found");
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
    return retryResolution(
      () -> delegate.resolve(plugin, pluginArtifact, dependencyFilter, repositories, session)
    );
  }

  @Override
  public DependencyResult resolvePlugin(Plugin plugin, Artifact artifact, DependencyFilter dependencyFilter,
                                        List<RemoteRepository> remotePluginRepositories,
                                        RepositorySystemSession repositorySession)
    throws PluginResolutionException {
    return retryResolution(
      () -> delegate.resolvePlugin(plugin, artifact, dependencyFilter, remotePluginRepositories, repositorySession)
    );
  }

  /*@Override - see IDEA-390821*/
  public DependencyResult resolvePluginAndFlatten(
    Plugin plugin,
    Artifact pluginArtifact,
    DependencyFilter dependencyFilter,
    List<RemoteRepository> repositories,
    RepositorySystemSession session)
    throws PluginResolutionException {
    MethodHandle handle = RESOLVE_PLUGIN_AND_FLATTEN;
    if (handle == null) {
      throw new AbstractMethodError(
        "Receiver class com.intellij.maven.server.m40.utils.Maven40PluginDependenciesResolver " +
        "does not define or inherit an implementation of the resolved method " +
        "'abstract org.eclipse.aether.resolution.DependencyResult resolvePluginAndFlatten(" +
        "org.apache.maven.model.Plugin, org.eclipse.aether.artifact.Artifact, " +
        "org.eclipse.aether.graph.DependencyFilter, java.util.List, " +
        "org.eclipse.aether.RepositorySystemSession)' " +
        "of interface org.apache.maven.plugin.internal.PluginDependenciesResolver.");
    }
    return retryResolution(
      () -> invokeResolvePluginAndFlatten(handle, plugin, pluginArtifact, dependencyFilter, repositories, session)
    );
  }

  private DependencyResult invokeResolvePluginAndFlatten(
    MethodHandle handle,
    Plugin plugin,
    Artifact pluginArtifact,
    DependencyFilter dependencyFilter,
    List<RemoteRepository> repositories,
    RepositorySystemSession session)
    throws PluginResolutionException {
    try {
      return (DependencyResult)handle.invoke(
        delegate, plugin, pluginArtifact, dependencyFilter, repositories, session);
    }
    catch (PluginResolutionException | RuntimeException | Error e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
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