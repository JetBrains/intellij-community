/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class Maven30ServerImpl extends MavenWatchdogAware implements MavenServer {
  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      Maven30ServerEmbedderImpl result = new Maven30ServerEmbedderImpl(settings.getSettings());
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenServerIndexer createIndexer(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      Maven3ServerIndexerImpl result = new Maven3ServerIndexerImpl(new Maven30ServerEmbedderImpl(new MavenServerSettings())) {
        @Override
        public Maven3ServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException {
          return new Maven30ServerEmbedderImpl(settings);
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
      return Maven30ServerEmbedderImpl.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven30ServerEmbedderImpl.assembleInheritance(model, parentModel);
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
      return Maven30ServerEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenPullServerLogger createPullLogger(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      MavenServerLoggerWrapper result = MavenServerGlobals.getLogger();
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
      MavenServerDownloadListenerWrapper result = MavenServerGlobals.getDownloadListener();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public synchronized void unreferenced() {
    System.exit(0);
  }
}
