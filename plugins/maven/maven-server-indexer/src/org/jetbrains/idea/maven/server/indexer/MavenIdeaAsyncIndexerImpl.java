// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import com.intellij.util.ExceptionUtilRt;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.RepositoryKind;
import org.jetbrains.idea.maven.model.MavenIndexId;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


public class MavenIdeaAsyncIndexerImpl extends MavenIdeaIndexerImpl implements AsyncMavenServerIndexer {
  static class IndexProcessData {
    public MavenIndexUpdateState state;
    public boolean isCanceled;
  }

  private final ExecutorService myExecutor = Executors.newSingleThreadExecutor(new MyThreadFactory());

  private final Map<String, IndexProcessData> states = new HashMap<>();

  public MavenIdeaAsyncIndexerImpl(PlexusContainer container) throws ComponentLookupException {
    super(container);
  }

  @Override
  public MavenIndexUpdateState startIndexing(MavenRepositoryInfo repositoryInfo, File indexDir, MavenToken token) {
    MavenServerUtil.checkToken(token);
    IndexProcessData data;
    synchronized (this) {
      IndexProcessData existingData = states.get(repositoryInfo.getUrl());
      if (existingData != null && existingData.state.myState == MavenIndexUpdateState.State.INDEXING) return existingData.state;
      data = new IndexProcessData();
      data.state = new MavenIndexUpdateState(repositoryInfo.getUrl(), null, null, MavenIndexUpdateState.State.INDEXING);
      states.put(repositoryInfo.getUrl(), data);
    }

    MavenServerProgressIndicator indicator = new MyMavenServerProgressIndicator(data);
    myExecutor.execute(() -> {
      try {
        runIndexing(repositoryInfo, indexDir, indicator);
        data.state.myState = MavenIndexUpdateState.State.SUCCEED;
      }
      catch (Exception e) {
        data.state.myState = MavenIndexUpdateState.State.FAILED;
        data.state.myError = ExceptionUtilRt.getThrowableText(e, "com.jetbrains");
        throw new RuntimeException(wrapException(e));
      }
    });
    return data.state;
  }

  @NotNull
  private static File createNonExistentDir(MavenRepositoryInfo repositoryInfo, File parent) {
    File result;
    do {
      result = new File(parent, repositoryInfo.getId() + System.nanoTime());
    }
    while (!result.mkdirs());
    return result;
  }

  private void runIndexing(MavenRepositoryInfo repositoryInfo, File tempDirectory, MavenServerProgressIndicator indicator)
    throws IOException, MavenServerProcessCanceledException, MavenServerIndexerException {
    String path = repositoryInfo.getKind() == RepositoryKind.LOCAL ? repositoryInfo.getUrl() : null;
    String url = repositoryInfo.getKind() == RepositoryKind.REMOTE ? repositoryInfo.getUrl() : null;
    MavenIndexId id =
      new MavenIndexId(repositoryInfo.getId(), repositoryInfo.getId(), path, url, tempDirectory.getAbsolutePath());
    doUpdateIndex(id, true, indicator);
  }

  @Override
  public ArrayList<MavenIndexUpdateState> status(MavenToken token) {
    MavenServerUtil.checkToken(token);
    ArrayList<MavenIndexUpdateState> result = new ArrayList<>(states.size());
    states.values().forEach(d -> result.add(d.state));
    return result;
  }

  @Override
  public void stopIndexing(MavenRepositoryInfo info, MavenToken token) {
    MavenServerUtil.checkToken(token);
    IndexProcessData data = states.get(info.getUrl());
    if (data != null) {
      data.isCanceled = true;
    }
  }

  private static class MyThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(@NotNull Runnable r) {
      Thread t = new Thread(r, "Maven Async Index thread");
      t.setDaemon(true);
      return t;
    }
  }

  private static class MyMavenServerProgressIndicator implements MavenServerProgressIndicator {
    private final IndexProcessData myData;

    private MyMavenServerProgressIndicator(IndexProcessData data) {

      myData = data;
    }

    @Override
    public void setText(String text) throws RemoteException {
      myData.state.myProgressInfo = text;
      myData.state.updateTimestamp();
    }

    @Override
    public void setText2(String text) throws RemoteException {

    }

    @Override
    public boolean isCanceled() throws RemoteException {
      return myData.isCanceled;
    }

    @Override
    public void setIndeterminate(boolean value) throws RemoteException {

    }

    @Override
    public void setFraction(double fraction) throws RemoteException {
      myData.state.fraction = fraction;
      myData.state.updateTimestamp();
    }
  }
}
