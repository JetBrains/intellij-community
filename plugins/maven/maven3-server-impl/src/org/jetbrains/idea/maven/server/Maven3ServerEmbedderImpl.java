// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;

import java.rmi.RemoteException;

public class Maven3ServerEmbedderImpl extends Maven3XServerEmbedder {
  public Maven3ServerEmbedderImpl(MavenEmbedderSettings settings) throws RemoteException {
    super(settings);
  }

  @Override
  protected void initLogging(Maven3ServerConsoleLogger consoleWrapper) {
    final RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
    final ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
    // Don't try calling setLoggerFactory() removed by MRESOLVER-36 when Maven 3.6.0+ is used.
    // For more information and link to the MRESOLVER-36 see IDEA-201282.
    final Maven3WrapperAetherLoggerFactory loggerFactory = new Maven3WrapperAetherLoggerFactory(consoleWrapper);

    if (artifactResolver instanceof DefaultArtifactResolver) {
      ((DefaultArtifactResolver)artifactResolver).setLoggerFactory(loggerFactory);
    }

    if (repositorySystem instanceof DefaultRepositorySystem) {
      ((DefaultRepositorySystem)repositorySystem).setLoggerFactory(loggerFactory);
    }
  }
}

