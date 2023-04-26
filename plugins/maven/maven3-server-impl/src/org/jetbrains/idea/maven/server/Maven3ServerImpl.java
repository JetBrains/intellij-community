// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.rmi.IdeaWatchdog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class Maven3ServerImpl extends MavenRemoteObject implements MavenServer {
  private volatile IdeaWatchdog myWatchdog;

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
  @NotNull
  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven3XProfileUtil.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven3XServerEmbedder.assembleInheritance(model, parentModel);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                MavenExplicitProfiles explicitProfiles,
                                                Collection<String> alwaysOnProfiles, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven3XProfileUtil.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenPullServerLogger createPullLogger(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      MavenServerLoggerWrapper result = Maven3ServerGlobals.getLogger();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenPullDownloadListener createPullDownloadListener(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      MavenServerDownloadListenerWrapper result = Maven3ServerGlobals.getDownloadListener();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public boolean ping(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    if (null == myWatchdog) return false;
    return myWatchdog.ping();
  }

  @Override
  public synchronized void unreferenced() {
    System.exit(0);
  }

  @Override
  public void setWatchdog(@NotNull IdeaWatchdog watchdog) {
    myWatchdog = watchdog;
  }
}
