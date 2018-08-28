// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class Maven3ServerImpl extends MavenRemoteObject implements MavenServer {
  @Override
  public void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) throws RemoteException {
    try {
      Maven3ServerGlobals.set(logger, downloadListener);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings) throws RemoteException {
    try {
      Maven3ServerEmbedderImpl result = new Maven3ServerEmbedderImpl(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenServerIndexer createIndexer() throws RemoteException {
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
      throw rethrowException(e);
    }
  }

  @Override
  @NotNull
  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    try {
      return Maven3ServerEmbedderImpl.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    try {
      return Maven3ServerEmbedderImpl.assembleInheritance(model, parentModel);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                MavenExplicitProfiles explicitProfiles,
                                                Collection<String> alwaysOnProfiles) {
    try {
      return Maven3ServerEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public synchronized void unreferenced() {
    System.exit(0);
  }
}
