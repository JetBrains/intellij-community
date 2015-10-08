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

import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class Maven30ServerImpl extends MavenRemoteObject implements MavenServer {
  public void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) throws RemoteException {
    try {
      Maven3ServerGlobals.set(logger, downloadListener);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public MavenServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException {
    try {
      Maven30ServerEmbedderImpl result = new Maven30ServerEmbedderImpl(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw rethrowException(e);
    }
  }

  public MavenServerIndexer createIndexer() throws RemoteException {
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
      throw rethrowException(e);
    }
  }

  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    try {
      return Maven30ServerEmbedderImpl.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    try {
      return Maven30ServerEmbedderImpl.assembleInheritance(model, parentModel);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                MavenExplicitProfiles explicitProfiles,
                                                Collection<String> alwaysOnProfiles) {
    try {
      return Maven30ServerEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
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
