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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.embedder.MavenFacadeEmbedderImpl;
import org.jetbrains.idea.maven.facade.embedder.MavenFacadeIndexerImpl;
import org.jetbrains.idea.maven.facade.embedder.MavenModelConverter;
import org.jetbrains.idea.maven.facade.nexus.ArtifactType;
import org.jetbrains.idea.maven.facade.nexus.Endpoint;
import org.jetbrains.idea.maven.facade.nexus.RepositoryType;
import org.jetbrains.idea.maven.facade.nexus.SearchResults;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import java.io.File;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class MavenFacadeImpl extends RemoteObject implements MavenFacade {
  public void setLogger(MavenFacadeLogger logger) throws RemoteException {
    MavenFacadeLoggerManager.setLogger(logger);
  }

  public MavenFacadeEmbedder createEmbedder(MavenFacadeSettings settings) throws RemoteException {
    MavenFacadeEmbedderImpl result = MavenFacadeEmbedderImpl.create(settings);
    UnicastRemoteObject.exportObject(result, 0);
    return result;
  }

  public MavenFacadeIndexer createIndexer() throws RemoteException {
    MavenFacadeIndexerImpl result = new MavenFacadeIndexerImpl();
    UnicastRemoteObject.exportObject(result, 0);
    return result;
  }

  public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    return MavenFacadeEmbedderImpl.interpolateAndAlignModel(model, basedir);
  }

  public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    return MavenFacadeEmbedderImpl.assembleInheritance(model, parentModel);
  }

  public ProfileApplicationResult applyProfiles(MavenModel model,
                                                File basedir,
                                                Collection<String> explicitProfiles,
                                                Collection<String> alwaysOnProfiles) {
    return MavenFacadeEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
  }

  public List<MavenRepositoryInfo> getRepositories(String nexusUrl) throws RemoteException {
    try {
      List<RepositoryType> repos = new Endpoint.Repositories(nexusUrl).getRepolistAsRepositories().getData().getRepositoriesItem();
      List<MavenRepositoryInfo> result = new ArrayList<MavenRepositoryInfo>(repos.size());
      for (RepositoryType repo : repos) {
        if (!"maven2".equals(repo.getProvider())) continue;
        result.add(MavenModelConverter.convertRepositoryInfo(repo));
      }
      return result;
    }
    catch (Exception e) {
      handleException(e);
      throw new AssertionError();
    }
  }

  @Nullable
  public List<MavenArtifactInfo> findArtifacts(MavenArtifactInfo template, String nexusUrl) throws RemoteException {
    try {
      SearchResults results = new Endpoint.DataIndex(nexusUrl)
        .getArtifactlistAsSearchResults(null, template.getGroupId(), template.getArtifactId(), template.getVersion(),
                                        template.getClassifier(), template.getClassNames());
      final boolean canTrySwitchGAV = template.getArtifactId() == null && template.getGroupId() != null;
      final boolean tooManyResults = results.isTooManyResults();
      if (canTrySwitchGAV && (tooManyResults || BigInteger.ZERO.equals(results.getTotalCount()))) {
        results = new Endpoint.DataIndex(nexusUrl)
          .getArtifactlistAsSearchResults(null, null, template.getGroupId(), template.getVersion(),
                                          template.getClassifier(), template.getClassNames());
      }
      if (tooManyResults || results.isTooManyResults()) return null;

      ArrayList<MavenArtifactInfo> result = new ArrayList<MavenArtifactInfo>();
      for (ArtifactType each : results.getData().getArtifact()) {
        result.add(MavenModelConverter.convertArtifactInfo(each));
      }
      return result;
    }
    catch (Exception ex) {
      handleException(ex);
      throw new AssertionError();
    }
  }
}
