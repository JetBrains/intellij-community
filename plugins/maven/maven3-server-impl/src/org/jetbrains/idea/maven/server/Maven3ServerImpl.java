// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Maven3ServerImpl extends MavenServerBase {
  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven3ServerEmbedderImpl result = new Maven3ServerEmbedderImpl(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
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
          return new Maven3ServerEmbedderImpl(new MavenEmbedderSettings(settings));
        }
      };
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenServerStatus getDebugStatus(boolean clean) {
    return new MavenServerStatus();
  }
}
