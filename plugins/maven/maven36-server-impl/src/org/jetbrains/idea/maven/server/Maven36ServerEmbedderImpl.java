// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactDescriptorReader;
import org.jetbrains.idea.maven.server.embedder.CustomMaven36ArtifactResolver;

import java.lang.reflect.Method;
import java.rmi.RemoteException;

public class Maven36ServerEmbedderImpl extends Maven3XServerEmbedder {
  private CustomMaven36ArtifactResolver enhancedArtifactResolver;
  private CustomMaven36ArtifactDescriptorReader enhancedArtifactDescriptorReader;

  public Maven36ServerEmbedderImpl(MavenEmbedderSettings settings) throws RemoteException {
    super(settings);
  }

  private synchronized void customizeArtifactResolver() {
    if (null == enhancedArtifactResolver) {
      ArtifactResolver defaultArtifactResolver = getComponent(ArtifactResolver.class);
      enhancedArtifactResolver = new CustomMaven36ArtifactResolver(defaultArtifactResolver);

      ArtifactDescriptorReader artifactDescriptorReader = getComponent(ArtifactDescriptorReader.class);
      if (artifactDescriptorReader instanceof DefaultArtifactDescriptorReader) {
        ((DefaultArtifactDescriptorReader)artifactDescriptorReader).setArtifactResolver(enhancedArtifactResolver);
      }

      RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
      if (repositorySystem instanceof DefaultRepositorySystem) {
        ((DefaultRepositorySystem)repositorySystem).setArtifactResolver(enhancedArtifactResolver);
      }
    }
  }

  private synchronized void customizeArtifactDescriptorReader() throws RemoteException {
    if (null == enhancedArtifactDescriptorReader) {
      ArtifactDescriptorReader artifactDescriptorReader = getComponent(ArtifactDescriptorReader.class);
      enhancedArtifactDescriptorReader = new CustomMaven36ArtifactDescriptorReader(artifactDescriptorReader);

      RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
      if (repositorySystem instanceof DefaultRepositorySystem) {
        ((DefaultRepositorySystem)repositorySystem).setArtifactDescriptorReader(enhancedArtifactDescriptorReader);
      }

      // depth-first dependency collector, available since maven 3.9.0
      DependencyCollector dependencyCollector = getComponentIfExists(DependencyCollector.class, "df");
      if (null != dependencyCollector) {
        // DependencyCollectorDelegate.setArtifactDescriptorReader
        try {
          Method method = dependencyCollector.getClass().getMethod("setArtifactDescriptorReader", ArtifactDescriptorReader.class);
          method.invoke(dependencyCollector, enhancedArtifactDescriptorReader);
        }
        catch (Throwable e) {
          Maven3ServerGlobals.getLogger().warn(e);
        }
      }
    }
  }

  private synchronized void resetArtifactResolver() {
    if (null != enhancedArtifactResolver) {
      enhancedArtifactResolver.reset();
    }
  }

  private synchronized void resetArtifactDescriptorReader() {
    if (null != enhancedArtifactDescriptorReader) {
      enhancedArtifactDescriptorReader.reset();
    }
  }

  @Override
  protected void customizeComponents(@Nullable MavenWorkspaceMap workspaceMap, boolean failOnUnresolvedDependency) throws RemoteException {
    super.customizeComponents(workspaceMap, failOnUnresolvedDependency);

    //TODO: registry key to turn off
    customizeArtifactResolver();
    customizeArtifactDescriptorReader();
  }

  @Override
  protected void resetCustomizedComponents() {
    super.resetCustomizedComponents();

    resetArtifactResolver();
    resetArtifactDescriptorReader();
  }

  @Override
  protected void initLogging(Maven3ServerConsoleLogger consoleWrapper) {
    Maven3Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }
}

