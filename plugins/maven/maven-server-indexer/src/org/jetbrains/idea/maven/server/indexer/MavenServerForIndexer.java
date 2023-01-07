// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import org.codehaus.plexus.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class MavenServerForIndexer extends MavenRemoteObject implements MavenServer {
  private volatile PlexusContainer myPlexusContainer;

  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Override
  public MavenServerIndexer createIndexer(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      MavenIdeaIndexerImpl result = new MavenIdeaIndexerImpl(getPlexusContainer());
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  private PlexusContainer getPlexusContainer() throws PlexusContainerException {
    if (myPlexusContainer != null) return myPlexusContainer;
    synchronized (this) {
      if (myPlexusContainer != null) return myPlexusContainer;
      final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
      config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
      myPlexusContainer = new DefaultPlexusContainer(config);

    }
    return myPlexusContainer;
  }

  @NotNull
  @Override
  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir, MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel, MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Override
  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                MavenExplicitProfiles explicitProfiles,
                                                Collection<String> alwaysOnProfiles,
                                                MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Nullable
  @Override
  public MavenPullServerLogger createPullLogger(MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Nullable
  @Override
  public MavenPullDownloadListener createPullDownloadListener(MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }
}
