// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Maven36ServerImpl extends MavenServerBase {
  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven36ServerEmbedderImpl result = new Maven36ServerEmbedderImpl(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (MavenCoreInitializationException e) {
      throw e;
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenServerIndexer createIndexer(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven3ServerIndexerImpl result = new Maven3ServerIndexerImpl(new Maven3ServerEmbedderImpl(new MavenEmbedderSettings(new MavenServerSettings()))) {
        @Override
        public Maven3ServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException {
          return new Maven36ServerEmbedderImpl(new MavenEmbedderSettings(settings));
        }
      };
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenServerStatus getDebugStatus(boolean clean) {
    MavenServerStatus status = new MavenServerStatus();
    if (!MavenServerStatsCollector.collectStatistics) return new MavenServerStatus();
    status.statusCollected = true;
    MavenServerStatsCollector.fill(status, clean);
    return status;
  }
}
