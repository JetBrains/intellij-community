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

import gnu.trove.TIntObjectHashMap;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Set;

public abstract class MavenIndexerWrapper extends RemoteObjectWrapper<MavenServerIndexer> {
  private final TIntObjectHashMap<IndexData> myDataMap = new TIntObjectHashMap<>();

  public MavenIndexerWrapper(@Nullable RemoteObjectWrapper<?> parent) {
    super(parent);
  }

  @Override
  protected synchronized void onError() {
    super.onError();
    for (int each : myDataMap.keys()) {
      myDataMap.get(each).remoteId = -1;
    }
  }

  public synchronized int createIndex(@NotNull final String indexId,
                                      @NotNull final String repositoryId,
                                      @Nullable final File file,
                                      @Nullable final String url,
                                      @NotNull final File indexDir) throws MavenServerIndexerException {
    IndexData data = new IndexData(indexId, repositoryId, file, url, indexDir);
    final int localId = System.identityHashCode(data);
    myDataMap.put(localId, data);

    perform(() -> getRemoteId(localId));

    return localId;
  }

  public synchronized void releaseIndex(int localId) throws MavenServerIndexerException {
    IndexData data = myDataMap.remove(localId);
    if (data == null) {
      MavenLog.LOG.warn("index " + localId + " not found");
      return;
    }

    // was invalidated on error
    if (data.remoteId == -1) return;

    MavenServerIndexer w = getWrappee();
    if (w == null) return;

    try {
      w.releaseIndex(data.remoteId);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public synchronized boolean indexExists(File dir) {
    try {
      return getOrCreateWrappee().indexExists(dir);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    return false;
  }

  public int getIndexCount() {
    return perform(() -> getOrCreateWrappee().getIndexCount());
  }

  public void updateIndex(final int localId,
                          final MavenGeneralSettings settings,
                          final MavenProgressIndicator indicator) throws MavenProcessCanceledException,
                                                                         MavenServerIndexerException {
    performCancelable(() -> {
      MavenServerProgressIndicator indicatorWrapper = MavenServerManager.wrapAndExport(indicator);
      try {
        getOrCreateWrappee().updateIndex(getRemoteId(localId), MavenServerManager.convertSettings(settings), indicatorWrapper);
      }
      finally {
        UnicastRemoteObject.unexportObject(indicatorWrapper, true);
      }
      return null;
    });
  }

  public void processArtifacts(final int indexId, final MavenIndicesProcessor processor) throws MavenServerIndexerException {
    perform(() -> {
      MavenServerIndicesProcessor processorWrapper = MavenServerManager.wrapAndExport(processor);
      try {
        getOrCreateWrappee().processArtifacts(getRemoteId(indexId), processorWrapper);
      }
      finally {
        UnicastRemoteObject.unexportObject(processorWrapper, true);
      }
      return null;
    });
  }

  public IndexedMavenId addArtifact(final int localId, final File artifactFile) throws MavenServerIndexerException {
    return perform(() -> getOrCreateWrappee().addArtifact(getRemoteId(localId), artifactFile));
  }

  public Set<MavenArtifactInfo> search(final int localId, final Query query, final int maxResult) throws MavenServerIndexerException {
    return perform(() -> getOrCreateWrappee().search(getRemoteId(localId), query, maxResult));
  }

  private synchronized int getRemoteId(int localId) throws RemoteException, MavenServerIndexerException {
    IndexData result = myDataMap.get(localId);
    MavenLog.LOG.assertTrue(result != null, "index " + localId + " not found");

    if (result.remoteId == -1) {
      result.remoteId = getOrCreateWrappee().createIndex(result.indexId, result.repositoryId, result.file, result.url, result.indexDir);
    }
    return result.remoteId;
  }

  public Collection<MavenArchetype> getArchetypes() {
    return perform(() -> getOrCreateWrappee().getArchetypes());
  }

  @TestOnly
  public void releaseInTests() {
    MavenServerIndexer w = getWrappee();
    if (w == null) return;
    try {
      w.release();
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  private static class IndexData {
    private int remoteId = -1;

    private final @NotNull String indexId;
    private final @NotNull String repositoryId;
    private final @Nullable File file;
    private final @Nullable String url;
    private final @NotNull File indexDir;

    public IndexData(String indexId, String repositoryId, File file, String url, File indexDir) {
      this.indexId = indexId;
      this.repositoryId = repositoryId;
      this.file = file;
      this.url = url;
      this.indexDir = indexDir;
    }
  }
}

