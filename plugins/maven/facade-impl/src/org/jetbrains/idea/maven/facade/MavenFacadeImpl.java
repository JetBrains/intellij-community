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
package org.jetbrains.idea.maven.facade;

import org.jetbrains.idea.maven.facade.embedder.MavenFacadeEmbedderImpl;
import org.jetbrains.idea.maven.facade.embedder.MavenFacadeIndexerImpl;
import org.jetbrains.idea.maven.model.MavenModel;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class MavenFacadeImpl extends MavenRemoteObject implements MavenFacade {
  public void set(MavenFacadeLogger logger, MavenFacadeDownloadListener downloadListener) throws RemoteException {
    try {
      MavenFacadeGlobalsManager.set(logger, downloadListener);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public MavenFacadeEmbedder createEmbedder(MavenFacadeSettings settings) throws RemoteException {
    try {
      MavenFacadeEmbedderImpl result = MavenFacadeEmbedderImpl.create(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw rethrowException(e);
    }
  }

  public MavenFacadeIndexer createIndexer() throws RemoteException {
    try {
      MavenFacadeIndexerImpl result = new MavenFacadeIndexerImpl();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw rethrowException(e);
    }
  }

  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    try {
      return MavenFacadeEmbedderImpl.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    try {
      return MavenFacadeEmbedderImpl.assembleInheritance(model, parentModel);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                Collection<String> explicitProfiles,
                                                Collection<String> alwaysOnProfiles) {
    try {
      return MavenFacadeEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
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
