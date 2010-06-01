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

import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MavenIndexerWrapper {
  private final MavenFacadeIndexer myWrappee;

  public MavenIndexerWrapper(MavenFacadeIndexer wrappee) {
    myWrappee = wrappee;
  }

  public int createIndex(@NotNull String indexId,
                         @Nullable String repositoryId,
                         @Nullable File file,
                         @Nullable String url,
                         @NotNull File indexDir) throws MavenFacadeIndexerException {
    try {
      return myWrappee.createIndex(indexId, repositoryId, file, url, indexDir);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void releaseIndex(int id) throws MavenFacadeIndexerException {
    try {
      myWrappee.releaseIndex(id);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public int getIndexCount() {
    try {
      return myWrappee.getIndexCount();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void updateIndex(int id, MavenGeneralSettings settings, MavenProgressIndicator indicator) throws MavenProcessCanceledException,
                                                                                                          MavenFacadeIndexerException {
    try {
      MavenFacadeProgressIndicator indicatorWrapper = MavenFacadeManager.wrapAndExport(indicator);
      try {
        myWrappee.updateIndex(id, MavenFacadeManager.convertSettings(settings), indicatorWrapper);
      }
      finally {
        UnicastRemoteObject.unexportObject(indicatorWrapper, true);
      }
    }
    catch (MavenFacadeProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public List<MavenId> getAllArtifacts(int indexId) throws MavenFacadeIndexerException {
    try {
      return myWrappee.getAllArtifacts(indexId);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public MavenId addArtifact(int indexId, File artifactFile) throws MavenFacadeIndexerException {
    try {
      return myWrappee.addArtifact(indexId, artifactFile);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public Set<MavenArtifactInfo> search(int indexId, Query query, int maxResult) throws MavenFacadeIndexerException {
    try {
      return myWrappee.search(indexId, query, maxResult);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public Collection<MavenArchetype> getArchetypes() {
    try {
      return myWrappee.getArchetypes();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void release() {
    try {
      myWrappee.release();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }
}

