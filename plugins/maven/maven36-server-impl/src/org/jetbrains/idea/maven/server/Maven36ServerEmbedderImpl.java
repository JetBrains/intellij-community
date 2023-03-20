// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactResolver;

import java.rmi.RemoteException;

public class Maven36ServerEmbedderImpl extends Maven3XServerEmbedder {
  private CustomMaven36ArtifactResolver enhancedArtifactResolver;

  public Maven36ServerEmbedderImpl(MavenEmbedderSettings settings) throws RemoteException {
    super(settings);
  }

  private synchronized void customizeArtifactResolver() {
    if (null == enhancedArtifactResolver) {
      ArtifactResolver defaultArtifactResolver = getComponent(ArtifactResolver.class);
      enhancedArtifactResolver = new CustomMaven36ArtifactResolver(defaultArtifactResolver);

      RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
      if (repositorySystem instanceof DefaultRepositorySystem) {
        ((DefaultRepositorySystem)repositorySystem).setArtifactResolver(enhancedArtifactResolver);
      }

      ArtifactDescriptorReader artifactDescriptorReader = getComponent(ArtifactDescriptorReader.class);
      if (artifactDescriptorReader instanceof DefaultArtifactDescriptorReader) {
        ((DefaultArtifactDescriptorReader)artifactDescriptorReader).setArtifactResolver(enhancedArtifactResolver);
      }
    }
  }

  private synchronized void resetArtifactResolver() {
    if (null != enhancedArtifactResolver) {
      enhancedArtifactResolver.reset();
    }
  }

  @Override
  protected void customizeComponents(@Nullable MavenWorkspaceMap workspaceMap, boolean failOnUnresolvedDependency) throws RemoteException {
    super.customizeComponents(workspaceMap, failOnUnresolvedDependency);

    //TODO: registry key to turn off
    customizeArtifactResolver();
  }

  @Override
  protected void resetCustomizedComponents() {
    super.resetCustomizedComponents();

    resetArtifactResolver();
  }

  @Override
  protected void initLogging(Maven3ServerConsoleLogger consoleWrapper) {
    Maven3Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }
}

