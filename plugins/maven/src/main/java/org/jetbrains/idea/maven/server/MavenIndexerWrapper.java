// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndices;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenIndexId;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public abstract class MavenIndexerWrapper extends MavenRemoteObjectWrapper<MavenServerIndexer> {


  public MavenIndexerWrapper(@Nullable RemoteObjectWrapper<?> parent) {
    super(parent);
  }

  public @Nullable MavenIndexUpdateState startIndexing(MavenRepositoryInfo info, File indexDir) {
    try {
      MavenServerIndexer w = getOrCreateWrappee();
      if (!(w instanceof AsyncMavenServerIndexer)) {
        MavenLog.LOG.warn("wrappee not an instance of AsyncMavenServerIndexer, is dedicated indexer enabled?");
        return null;
      }
      return ((AsyncMavenServerIndexer)w).startIndexing(info, indexDir, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    return null;
  }

  public void stopIndexing(MavenRepositoryInfo info) {
    try {
      MavenServerIndexer w = getOrCreateWrappee();
      if (!(w instanceof AsyncMavenServerIndexer)) {
        MavenLog.LOG.warn("wrappee not an instance of AsyncMavenServerIndexer, is dedicated indexer enabled?");
        return;
      }
      ((AsyncMavenServerIndexer)w).stopIndexing(info, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public List<MavenIndexUpdateState> status() {

    try {
      MavenServerIndexer w = getOrCreateWrappee();
      if (!(w instanceof AsyncMavenServerIndexer)) {
        MavenLog.LOG.warn("wrappee not an instance of AsyncMavenServerIndexer, is dedicated indexer enabled?");
        return Collections.emptyList();
      }
      return ((AsyncMavenServerIndexer)w).status(ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    return Collections.emptyList();
  }

  public void releaseIndex(MavenIndexId mavenIndexId) throws MavenServerIndexerException {
    MavenLog.LOG.debug("releaseIndex " + mavenIndexId.indexId);

    MavenServerIndexer w = getWrappee();
    if (w == null) return;

    try {
      w.releaseIndex(mavenIndexId, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public boolean indexExists(File dir) {
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

  public void updateIndex(@NotNull final MavenIndexId mavenIndexId,
                          @NotNull final MavenProgressIndicator indicator,
                          boolean multithreaded) throws MavenProcessCanceledException,
                                                        MavenServerIndexerException {
    performCancelable(() -> {
      MavenServerProgressIndicator indicatorWrapper = wrapAndExport(indicator);
      try {
        getOrCreateWrappee().updateIndex(mavenIndexId, indicatorWrapper, multithreaded, ourToken);
      }
      finally {
        UnicastRemoteObject.unexportObject(indicatorWrapper, true);
      }
      return null;
    });
  }

  public void processArtifacts(final MavenIndexId mavenIndexId, final MavenIndicesProcessor processor, MavenProgressIndicator progress)
    throws MavenServerIndexerException {
    perform(() -> {
      try {
        int start = 0;
        List<IndexedMavenId> list;
        do {
          if (progress.isCanceled()) return null;
          MavenLog.LOG.debug("process artifacts: " + start);
          list = getOrCreateWrappee().processArtifacts(mavenIndexId, start, ourToken);
          if (list != null) {
            processor.processArtifacts(list);
            start += list.size();
          }
        }
        while (list != null);
        return null;
      }
      catch (Exception e) {
        return null;
      }
    });
  }

  @NotNull
  public List<AddArtifactResponse> addArtifacts(MavenIndexId mavenIndexId, Collection<? extends File> artifactFiles) {
    return perform(() -> {
      try {
        return getOrCreateWrappee().addArtifacts(mavenIndexId, new ArrayList<>(artifactFiles), ourToken);
      }
      catch (Throwable ignore) {
        return ContainerUtil.map(artifactFiles, file -> new AddArtifactResponse(file, null));
      }
    });
  }

  public Set<MavenArtifactInfo> search(final MavenIndexId mavenIndexId, final String pattern, final int maxResult)
    throws MavenServerIndexerException {
    return perform(() -> getOrCreateWrappee().search(mavenIndexId, pattern, maxResult, ourToken));
  }

  @ApiStatus.Internal
  public MavenIndices getOrCreateIndices(Project project) {
    return createMavenIndices(project);
  }

  protected abstract MavenIndices createMavenIndices(Project project);
}

