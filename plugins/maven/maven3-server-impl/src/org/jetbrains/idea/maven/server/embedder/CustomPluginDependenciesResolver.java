// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Edoardo Luppi
 */
public class CustomPluginDependenciesResolver implements PluginDependenciesResolver {
  private final PluginDependenciesResolver myDelegate;

  public CustomPluginDependenciesResolver(@NotNull final PluginDependenciesResolver delegate) {
    myDelegate = delegate;
  }

  @Override
  public Artifact resolve(
    final Plugin plugin,
    final List<RemoteRepository> list,
    final RepositorySystemSession session
  ) throws PluginResolutionException {
    return myDelegate.resolve(plugin, list, session);
  }

  @Override
  public DependencyNode resolve(
    final Plugin plugin,
    final Artifact artifact,
    final DependencyFilter filter,
    final List<RemoteRepository> list,
    final RepositorySystemSession session
  ) throws PluginResolutionException {
    final DependencyNode root = myDelegate.resolve(plugin, artifact, filter, list, session);
    return root != null
           ? new CustomDependencyNode(root)
           : null;
  }
}
