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
import org.jetbrains.idea.maven.server.embedder.Maven2ServerEmbedderImpl;
import org.jetbrains.idea.maven.server.embedder.Maven2ServerIndexerImpl;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class Maven2ServerImpl extends MavenRemoteObject implements MavenServer {
  @Override
  public void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven2ServerGlobals.set(logger, downloadListener);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenServerEmbedder createEmbedder(MavenEmbedderSettings settings, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven2ServerEmbedderImpl result = Maven2ServerEmbedderImpl.create(settings.getSettings());
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenServerIndexer createIndexer(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Maven2ServerIndexerImpl result = new Maven2ServerIndexerImpl();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw rethrowException(e);
    }
  }

  @Override
  @NotNull
  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven2ServerEmbedderImpl.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven2ServerEmbedderImpl.assembleInheritance(model, parentModel);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                MavenExplicitProfiles explicitProfiles,
                                                Collection<String> alwaysOnProfiles, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven2ServerEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
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
