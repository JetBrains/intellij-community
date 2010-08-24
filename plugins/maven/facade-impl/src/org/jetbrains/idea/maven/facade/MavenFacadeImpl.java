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

import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.embedder.MavenFacadeEmbedderImpl;
import org.jetbrains.idea.maven.facade.embedder.MavenFacadeIndexerImpl;
import org.jetbrains.idea.maven.facade.embedder.MavenModelConverter;
import org.jetbrains.idea.maven.facade.nexus.ArtifactType;
import org.jetbrains.idea.maven.facade.nexus.Endpoint;
import org.jetbrains.idea.maven.facade.nexus.SearchResults;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MavenFacadeImpl extends RemoteObject implements MavenFacade {
  @Override
  public void ping() throws RemoteException {
    // no op
  }

  public void set(MavenFacadeLogger logger, MavenFacadeDownloadListener downloadListener) throws RemoteException {
    try {
      MavenFacadeGlobalsManager.set(logger, downloadListener);
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  public MavenFacadeEmbedder createEmbedder(MavenFacadeSettings settings) throws RemoteException {
    try {
      MavenFacadeEmbedderImpl result = MavenFacadeEmbedderImpl.create(settings);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  public MavenFacadeIndexer createIndexer() throws RemoteException {
    try {
      MavenFacadeIndexerImpl result = new MavenFacadeIndexerImpl();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    try {
      return MavenFacadeEmbedderImpl.interpolateAndAlignModel(model, basedir);
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    try {
      return MavenFacadeEmbedderImpl.assembleInheritance(model, parentModel);
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
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
      throw new RuntimeException(wrapException(e));
    }
  }

  public List<MavenRepositoryInfo> getRepositories(String url) throws RemoteException {
    try {
      for (MavenManagementService service : MavenManagementService.getServices()) {
        try {
          final List<MavenRepositoryInfo> repositories = service.getRepositories(url);
          if (repositories != null) {
            return repositories;
          }
        }
        catch (JAXBException e) {
          // ignore
        }
        catch (JsonParseException e) {
          // ignore
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
    throw new RuntimeException("Unknown Repository Service. Supported Services are "+ Arrays.asList(MavenManagementService.getServices()));
  }

  @Nullable
  public List<MavenArtifactInfo> findArtifacts(MavenArtifactInfo template, String url) throws RemoteException {
    try {
      List<MavenArtifactInfo> result = null;
      for (MavenManagementService service : MavenManagementService.getServices()) {
        try {
          final List<MavenArtifactInfo> artifacts = service.findArtifacts(template, url);
          if (result == null) result = artifacts;
          result.addAll(artifacts);
        }
        catch (JAXBException e) {
          // ignore
        }
        catch (JsonParseException e) {
          // ignore
        }
      }
      if (result != null) return result;
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
    throw new RuntimeException("Unknown Repository Service. Supported Services are " + Arrays.asList(MavenManagementService.getServices()));
  }

  @Override
  public void unreferenced() {
    System.exit(0);
  }
}
