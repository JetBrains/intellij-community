// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public abstract class MavenIndexerWrapper extends MavenRemoteObjectWrapper<MavenServerIndexer> {
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
      w.releaseIndex(data.remoteId, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public synchronized boolean indexExists(File dir) {
    try {
      return getOrCreateWrappee().indexExists(dir, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    return false;
  }

  public int getIndexCount() {
    return perform(() -> getOrCreateWrappee().getIndexCount(ourToken));
  }

  public void updateIndex(final int localId,
                          final MavenGeneralSettings settings,
                          final MavenProgressIndicator indicator) throws MavenProcessCanceledException,
                                                                         MavenServerIndexerException {
    performCancelable(() -> {
      MavenServerProgressIndicator indicatorWrapper = wrapAndExport(indicator);
      try {
        getOrCreateWrappee().updateIndex(getRemoteId(localId), MavenServerManager.convertSettings(settings), indicatorWrapper, ourToken);
      }
      finally {
        UnicastRemoteObject.unexportObject(indicatorWrapper, true);
      }
      return null;
    });
  }

  public void processArtifacts(final int indexId, final MavenIndicesProcessor processor) throws MavenServerIndexerException {
    perform(() -> {
      MavenServerIndicesProcessor processorWrapper = wrapAndExport(processor);
      try {
        getOrCreateWrappee().processArtifacts(getRemoteId(indexId), processorWrapper, ourToken);
      }
      finally {
        UnicastRemoteObject.unexportObject(processorWrapper, true);
      }
      return null;
    });
  }

  public IndexedMavenId addArtifact(final int localId, final File artifactFile) throws MavenServerIndexerException {
    return perform(() -> getOrCreateWrappee().addArtifact(getRemoteId(localId), artifactFile, ourToken));
  }

  public Set<MavenArtifactInfo> search(final int localId, final Query query, final int maxResult) throws MavenServerIndexerException {
    return perform(() -> getOrCreateWrappee().search(getRemoteId(localId), query, maxResult, ourToken));
  }

  private synchronized int getRemoteId(int localId) throws RemoteException, MavenServerIndexerException {
    IndexData result = myDataMap.get(localId);
    MavenLog.LOG.assertTrue(result != null, "index " + localId + " not found");

    if (result.remoteId == -1) {
      result.remoteId = getOrCreateWrappee().createIndex(result.indexId, result.repositoryId, result.file, result.url, result.indexDir,
                                                         ourToken);
    }
    return result.remoteId;
  }

  public Collection<MavenArchetype> getArchetypes() {
    return perform(() -> getOrCreateWrappee().getArchetypes(ourToken));
  }

  @TestOnly
  public void releaseInTests() {
    MavenServerIndexer w = getWrappee();
    if (w == null) return;
    try {
      w.release(ourToken);
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

    IndexData(@NotNull String indexId, @NotNull String repositoryId, @Nullable File file, @Nullable String url, @NotNull File indexDir) {
      this.indexId = indexId;
      this.repositoryId = repositoryId;
      this.file = file;
      this.url = url;
      this.indexDir = indexDir;
    }
  }

  private MavenServerIndicesProcessor wrapAndExport(final MavenIndicesProcessor processor) {
    return doWrapAndExport(new RemoteMavenServerIndicesProcessor(processor));
  }


  private static final class RemoteMavenServerIndicesProcessor extends MavenRemoteObject implements MavenServerIndicesProcessor {
    private final MavenIndicesProcessor myProcessor;

    private RemoteMavenServerIndicesProcessor(MavenIndicesProcessor processor) {
      myProcessor = processor;
    }

    @Override
    public void processArtifacts(Collection<IndexedMavenId> artifacts) {
      myProcessor.processArtifacts(artifacts);
    }
  }

}

