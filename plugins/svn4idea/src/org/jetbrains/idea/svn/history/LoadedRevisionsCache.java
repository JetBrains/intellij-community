// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.ChangesBunch;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesListener;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LoadedRevisionsCache implements Disposable {
  private static final Logger LOG = Logger.getInstance(LoadedRevisionsCache.class);

  private final Project myProject;
  private final Map<String, Bunch> myMap;
  private final Object refreshLock = new Object();
  private long myRefreshTime;
  private final MessageBusConnection myConnection;

  public static LoadedRevisionsCache getInstance(final Project project) {
    return project.getService(LoadedRevisionsCache.class);
  }

  private LoadedRevisionsCache(final Project project) {
    myProject = project;
    myMap = (ApplicationManager.getApplication().isUnitTestMode()) ? new HashMap<>() : ContainerUtil.createSoftMap();

    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesListener() {
      @Override
      public void changesLoaded(@NotNull RepositoryLocation location, @NotNull List<CommittedChangeList> changes) {
        ApplicationManager.getApplication().invokeLater(() -> {
          myMap.clear();
          setRefreshTime(System.currentTimeMillis());
        });
      }
    });
    setRefreshTime(0);
  }

  private long getRefreshTime() {
    synchronized (refreshLock) {
      return myRefreshTime;
    }
  }

  private void setRefreshTime(final long refreshTime) {
    synchronized (refreshLock) {
      myRefreshTime = refreshTime;
    }
  }

  private static void debugInfo(@NotNull List<CommittedChangeList> data, final boolean consistentWithPrevious, final Bunch bindTo) {
    LOG.debug(">>> cache internal >>> consistent: " + consistentWithPrevious + " bindTo: " + bindTo +
             " oldest list: " + data.get(data.size() - 1).getNumber() + ", youngest list: " + data.get(0).getNumber());
  }

  @Override
  public void dispose() {
    // TODO: Seems that dispose could be removed as connection will be disposed anyway on project dispose and clearing map is not necessary
    myConnection.disconnect();
    myMap.clear();
  }

  @NotNull
  private static List<List<CommittedChangeList>> split(final List<CommittedChangeList> list, final int size) {
    final int listSize = list.size();
    if (listSize < size) {
      return Collections.singletonList(list);
    }
    final int first = listSize % size;

    int start = 0;
    int end = (first == 0) ? (Math.min(listSize, size)) : first;
    final List<List<CommittedChangeList>> result = new ArrayList<>(listSize / size + 1);
    while (start < listSize) {
      result.add(list.subList(start, end));
      start = end;
      end += size;
    }
    return result;
  }

  @Nullable
  public Bunch put(final List<CommittedChangeList> data, final boolean consistentWithPrevious, final Bunch bindTo) {
    if (data.isEmpty()) {
      return null;
    }
    final SvnRepositoryLocation repositoryLocation = ((SvnChangeList) data.get(0)).getLocation();
    final String location = repositoryLocation.getURL();

    final List<List<CommittedChangeList>> list = split(data, SvnRevisionsNavigationMediator.CHUNK_SIZE);

    Bunch bindToBunch = bindTo;
    if (bindToBunch == null) {
      final Bunch fromCache = myMap.get(location);
      if (fromCache != null) {
        final long passedSmallestNumber = data.get(data.size() - 1).getNumber();
        final List<CommittedChangeList> cachedList = fromCache.getList();
        final long greatestNumber = cachedList.get(0).getNumber();
        if (greatestNumber < passedSmallestNumber) {
          bindToBunch = fromCache;
        }
      }
    }
    boolean consistent = consistentWithPrevious;
    for (int i = list.size() - 1; i >= 0; -- i) {
      final List<CommittedChangeList> changeLists = list.get(i);
      debugInfo(changeLists, consistent, bindToBunch);
      bindToBunch = new Bunch(changeLists, consistent, bindToBunch);
      consistent = true;
    }

    if (bindToBunch != null) {
      myMap.put(location, bindToBunch);
    }
    return bindToBunch;
  }

  @Nullable
  public Iterator<ChangesBunch> iterator(final String location) {
    final Bunch bunch = myMap.get(location);
    if (bunch == null) {
      return null;
    }
    return new BunchIterator(bunch);
  }

  private final class BunchIterator implements Iterator<ChangesBunch> {
    private final long myCreationTime;
    private Bunch myBunch;

    private BunchIterator(final Bunch bunch) {
      myBunch = bunch;
      myCreationTime = System.currentTimeMillis();
    }

    private void checkValidity() {
      ApplicationManager.getApplication().assertIsDispatchThread();

      if (myCreationTime <= getRefreshTime()) {
        throw new SwitchRevisionsProviderException();
      }
    }

    @Override
    public boolean hasNext() {
      checkValidity();
      return myBunch != null;
    }

    @Override
    public ChangesBunch next() {
      checkValidity();
      final Bunch current = myBunch;
      myBunch = myBunch.myNext;
      return current;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public static final class Bunch extends ChangesBunch {
    private final Bunch myNext;

    private Bunch(final List<CommittedChangeList> list, final boolean consistent, final Bunch next) {
      super(list, consistent);
      myNext = next;
    }

    public Bunch getNext() {
      return myNext;
    }
  }
}
