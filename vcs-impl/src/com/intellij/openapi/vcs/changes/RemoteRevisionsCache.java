package com.intellij.openapi.vcs.changes;

import com.intellij.lifecycle.ControlledAlarmFactory;
import com.intellij.lifecycle.SlowlyClosingAlarm;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.RemoteStatusChangeNodeDecorator;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class RemoteRevisionsCache implements PlusMinus<Pair<String, AbstractVcs>>, VcsListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.RemoteRevisionsCache");

  public static Topic<Runnable> REMOTE_VERSION_CHANGED  = new Topic<Runnable>("REMOTE_VERSION_CHANGED", Runnable.class);

  // every 5 minutes.. (time unit to check for server commits)
  private static final long ourRottenPeriod = 300 * 1000;
  private final Map<String, Pair<VcsRoot, VcsRevisionNumber>> myData;
  private final Map<VcsRoot, LazyRefreshingSelfQueue<String>> myRefreshingQueues;
  private final Map<String, VcsRevisionNumber> myLatestRevisionsMap;
  private final ProjectLevelVcsManager myVcsManager;
  private final LocalFileSystem myLfs;

  private final Object myLock;

  private final RemoteStatusChangeNodeDecorator myChangeDecorator;

  public static final VcsRevisionNumber NOT_LOADED = new VcsRevisionNumber() {
    public String asString() {
      return "NOT_LOADED";
    }

    public int compareTo(VcsRevisionNumber o) {
      if (o == this) return 0;
      return -1;
    }
  };
  public static final VcsRevisionNumber UNKNOWN = new VcsRevisionNumber() {
    public String asString() {
      return "UNKNOWN";
    }

    public int compareTo(VcsRevisionNumber o) {
      if (o == this) return 0;
      return -1;
    }
  };

  public static RemoteRevisionsCache getInstance(final Project project) {
    return ServiceManager.getService(project, RemoteRevisionsCache.class);
  }

  private RemoteRevisionsCache(final Project project) {
    myLock = new Object();
    myData = new HashMap<String, Pair<VcsRoot, VcsRevisionNumber>>();
    myRefreshingQueues = Collections.synchronizedMap(new HashMap<VcsRoot, LazyRefreshingSelfQueue<String>>());
    myLatestRevisionsMap = new HashMap<String, VcsRevisionNumber>();
    myLfs = LocalFileSystem.getInstance();
    myChangeDecorator = new RemoteStatusChangeNodeDecorator(this);

    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myVcsManager.addVcsListener(this);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        myVcsManager.removeVcsListener(RemoteRevisionsCache.this);
      }
    });
    final MyRecursiveUpdateRequest request = new MyRecursiveUpdateRequest(project, new Runnable() {
      public void run() {
        final List<LazyRefreshingSelfQueue<String>> list = new ArrayList<LazyRefreshingSelfQueue<String>>();
        synchronized (myLock) {
          list.addAll(myRefreshingQueues.values());
        }
        LOG.debug("queues refresh started, queues: " + list.size());
        for (LazyRefreshingSelfQueue<String> queue : list) {
          queue.updateStep();
        }
      }
    });
    request.start();
  }

  private static class MyRecursiveUpdateRequest implements Runnable {
    private final Alarm mySimpleAlarm;
    private final SlowlyClosingAlarm myControlledAlarm;
    // this interval is also to check for not initialized paths, so it is rather small
    private static final int ourRefreshInterval = 1000;
    private final Runnable myRunnable;

    private MyRecursiveUpdateRequest(final Project project, final Runnable runnable) {
      myRunnable = new Runnable() {
        public void run() {
          try {
            runnable.run();
          } catch (ProcessCanceledException e) {
            //
          } catch (RuntimeException e) {
            LOG.info(e);
          }
          mySimpleAlarm.addRequest(MyRecursiveUpdateRequest.this, ourRefreshInterval);
        }
      };
      mySimpleAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
      myControlledAlarm = ControlledAlarmFactory.createOnApplicationPooledThread(project);
    }

    public void start() {
      mySimpleAlarm.addRequest(this, ourRefreshInterval);
    }

    public void run() {
      try {
        myControlledAlarm.checkShouldExit();
        myControlledAlarm.addRequest(myRunnable);
      } catch (ProcessCanceledException e) {
        //
      }
    }
  }

  public void directoryMappingChanged() {
    synchronized (myLock) {
      for (Map.Entry<String, Pair<VcsRoot, VcsRevisionNumber>> entry : myData.entrySet()) {
        final String key = entry.getKey();
        final VcsRoot storedVcsRoot = entry.getValue().getFirst();
        final VirtualFile vf = myLfs.refreshAndFindFileByIoFile(new File(key));
        final AbstractVcs newVcs = (vf == null) ? null : myVcsManager.getVcsFor(vf);
        final VirtualFile newRoot = getRootForPath(key);
        final VcsRoot newVcsRoot = new VcsRoot(newVcs, newRoot);

        if (newVcs == null) {
          myData.remove(key);
          getQueue(storedVcsRoot).forceRemove(key);
        } else if (! storedVcsRoot.equals(newVcsRoot)) {
          switchVcs(storedVcsRoot, newVcsRoot, key);
        }
      }
    }
  }

  private void switchVcs(final VcsRoot oldVcsRoot, final VcsRoot newVcsRoot, final String key) {
    synchronized (myLock) {
      final LazyRefreshingSelfQueue<String> oldQueue = getQueue(oldVcsRoot);
      final LazyRefreshingSelfQueue<String> newQueue = getQueue(newVcsRoot);
      myData.put(key, new Pair<VcsRoot, VcsRevisionNumber>(newVcsRoot, NOT_LOADED));
      oldQueue.forceRemove(key);
      newQueue.addRequest(key);
    }
  }

  public void plus(final Pair<String, AbstractVcs> pair) {
    // does not support
    if (pair.getSecond().getDiffProvider() == null) return;

    final String key = pair.getFirst();
    final AbstractVcs newVcs = pair.getSecond();

    final VirtualFile root = getRootForPath(key);
    if (root == null) return;

    final VcsRoot vcsRoot = new VcsRoot(newVcs, root);

    synchronized (myLock) {
      final Pair<VcsRoot, VcsRevisionNumber> value = myData.get(key);
      if (value == null) {
        final LazyRefreshingSelfQueue<String> queue = getQueue(vcsRoot);
        myData.put(key, new Pair<VcsRoot, VcsRevisionNumber>(vcsRoot, NOT_LOADED));
        queue.addRequest(key);
      } else if (! value.getFirst().equals(vcsRoot)) {
        switchVcs(value.getFirst(), vcsRoot, key);
      }
    }
  }

  public void invalidate(final String path) {
    synchronized (myLock) {
      final Pair<VcsRoot, VcsRevisionNumber> pair = myData.remove(path);
      if (pair != null) {
        // vcs [root] seems to not change
        final VcsRoot vcsRoot = pair.getFirst();
        final LazyRefreshingSelfQueue<String> queue = getQueue(vcsRoot);
        queue.forceRemove(path);
        queue.addRequest(path);
        myData.put(path, new Pair<VcsRoot, VcsRevisionNumber>(vcsRoot, NOT_LOADED));
      }
    }
  }

  @Nullable
  private VirtualFile getRootForPath(final String s) {
    return myVcsManager.getVcsRootFor(new FilePathImpl(new File(s), false));
  }

  public void minus(Pair<String, AbstractVcs> pair) {
    // does not support
    if (pair.getSecond().getDiffProvider() == null) return;
    final VirtualFile root = getRootForPath(pair.getFirst());
    if (root == null) return;

    final LazyRefreshingSelfQueue<String> queue;
    final String key = pair.getFirst();
    synchronized (myLock) {
      queue = getQueue(new VcsRoot(pair.getSecond(), root));
      myData.remove(key);
    }
    queue.forceRemove(key);
  }

  // +-
  @NotNull
  private LazyRefreshingSelfQueue<String> getQueue(final VcsRoot vcsRoot) {
    synchronized (myLock) {
      LazyRefreshingSelfQueue<String> queue = myRefreshingQueues.get(vcsRoot);
      if (queue != null) return queue;

      queue = new LazyRefreshingSelfQueue<String>(ourRottenPeriod, new MyShouldUpdateChecker(vcsRoot), new MyUpdater(vcsRoot));
      myRefreshingQueues.put(vcsRoot, queue);
      return queue;
    }
  }

  private class MyUpdater implements Consumer<String> {
    private final VcsRoot myVcsRoot;

    public MyUpdater(final VcsRoot vcsRoot) {
      myVcsRoot = vcsRoot;
    }

    public void consume(String s) {
      LOG.debug("update for: " + s);
      final VirtualFile vf = myLfs.refreshAndFindFileByIoFile(new File(s));
      final ItemLatestState state;
      final DiffProvider diffProvider = myVcsRoot.vcs.getDiffProvider();
      if (vf == null) {
        // doesnt matter if directory or not
        state = diffProvider.getLastRevision(FilePathImpl.createForDeletedFile(new File(s), false));
      } else {
        state = diffProvider.getLastRevision(vf);
      }
      final VcsRevisionNumber newNumber = state.getNumber();

      final Pair<VcsRoot, VcsRevisionNumber> oldPair;
      synchronized (myLock) {
        oldPair = myData.get(s);
        myData.put(s, new Pair<VcsRoot, VcsRevisionNumber>(myVcsRoot, newNumber));
      }
      
      if ((oldPair == null) || (oldPair != null) && (oldPair.getSecond().compareTo(newNumber) != 0)) {
        LOG.debug("refresh triggered by " + s);
        myVcsRoot.vcs.getProject().getMessageBus().syncPublisher(REMOTE_VERSION_CHANGED).run();
      }
    }
  }

  private class MyShouldUpdateChecker implements Computable<Boolean> {
    private final VcsRoot myVcsRoot;

    public MyShouldUpdateChecker(final VcsRoot vcsRoot) {
      myVcsRoot = vcsRoot;
    }

    public Boolean compute() {
      final AbstractVcs vcs = myVcsRoot.vcs;
      // won't be called in parallel for same vcs -> just synchronized map is ok
      final String vcsName = vcs.getName();
      LOG.debug("should update for: " + vcsName + " root: " + myVcsRoot.path.getPath());
      final VcsRevisionNumber latestNew = vcs.getDiffProvider().getLatestCommittedRevision(myVcsRoot.path);

      final VcsRevisionNumber latestKnown = myLatestRevisionsMap.get(vcsName);
      // not known
      if (latestNew == null) return true;
      if ((latestKnown == null) || (latestNew.compareTo(latestKnown) != 0)) {
        myLatestRevisionsMap.put(vcsName, latestNew);
        return true;
      }
      return false;
    }
  }

  public VcsRevisionNumber getNumber(final String path) {
    synchronized (myLock) {
      final Pair<VcsRoot, VcsRevisionNumber> pair = myData.get(path);
      return pair == null ? NOT_LOADED : pair.getSecond();
    }
  }

  public boolean getState(final Change change) {
    return getRevisionState(change.getBeforeRevision()) & getRevisionState(change.getAfterRevision());
  }

  private boolean getRevisionState(final ContentRevision revision) {
    if (revision != null) {
      final VcsRevisionNumber local = revision.getRevisionNumber();
      final String path = revision.getFile().getIOFile().getAbsolutePath();
      final VcsRevisionNumber remote = getNumber(path);
      if ((NOT_LOADED == remote) || (UNKNOWN == remote)) {
        return true;
      }
      return local.compareTo(remote) == 0;
    }
    return true;
  }

  public RemoteStatusChangeNodeDecorator getChangesNodeDecorator() {
    return myChangeDecorator;
  }
}
