// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenEmbedderSettings;
import org.jetbrains.idea.maven.server.MavenPullDownloadListener;
import org.jetbrains.idea.maven.server.MavenPullServerLogger;
import org.jetbrains.idea.maven.server.MavenServer;
import org.jetbrains.idea.maven.server.MavenServerEmbedder;
import org.jetbrains.idea.maven.server.MavenServerIndexer;
import org.jetbrains.idea.maven.server.MavenServerStatus;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.server.MavenWatchdogAware;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class MavenServerForIndexer extends MavenWatchdogAware implements MavenServer {
  private volatile MavenIdeaIndexerImpl myIndexerRef;
  private volatile PlexusContainer myPlexusContainer;

  public MavenServerForIndexer() {
    String logLevel = System.getProperty("maven.indexer.log.level", "error");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel);

    Level utilLevel = getLogLevel(logLevel);
    Logger rootLogger = LogManager.getLogManager().getLogger("");
    Handler[] handlers = rootLogger.getHandlers();
    rootLogger.setLevel(utilLevel);
    for (Handler h : handlers) {
      h.setLevel(utilLevel);
    }
  }

  private static Level getLogLevel(String level) {
    switch (level) {
      case "error":
        return Level.SEVERE;
      case "debug":
        return Level.ALL;
      default:
        return Level.INFO;
    }
  }

  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Override
  public MavenServerIndexer createIndexer(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    if (myIndexerRef != null) {
      return myIndexerRef;
    }
    synchronized (this) {
      if (myIndexerRef != null) {
        return myIndexerRef;
      }
      MavenIdeaIndexerImpl result = null;
      try {
        result = new MavenIdeaAsyncIndexerImpl(getPlexusContainer());
        UnicastRemoteObject.exportObject(result, 0);
        myIndexerRef = result;
      }
      catch (Exception e) {
        try {
          if (result != null) {
            UnicastRemoteObject.unexportObject(result, true);
          }
        }
        catch (Exception unexportException) {
          RuntimeException re = wrapToSerializableRuntimeException(e);
          throw re;
        }

        throw wrapToSerializableRuntimeException(e);
      }
    }
    return myIndexerRef;
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

  @Override
  public @Nullable MavenPullServerLogger createPullLogger(MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Override
  public @Nullable MavenPullDownloadListener createPullDownloadListener(MavenToken token) throws RemoteException {
    throw new UnsupportedOperationException("indexing server");
  }

  @Override
  public MavenServerStatus getDebugStatus(boolean clean) {
    MavenServerStatus result = new MavenServerStatus();
    result.statusCollected = false;
    return result;
  }
}
