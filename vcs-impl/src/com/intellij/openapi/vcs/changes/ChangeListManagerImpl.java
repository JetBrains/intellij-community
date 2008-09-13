package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.Topic;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManagerEx implements ProjectComponent, ChangeListOwner, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListManagerImpl");

  private Project myProject;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private static ScheduledExecutorService ourUpdateAlarm = ConcurrencyUtil.newSingleScheduledThreadExecutor("Change List Updater");

  // just markers
  private final List<Runnable> myWaitingUpdateCompletionQueue;

  private ScheduledFuture<?> myCurrentUpdate = null;

  private boolean myInitialized = false;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private boolean myDisposed = false;

  private VirtualFileHolder myUnversionedFilesHolder;
  private VirtualFileHolder myModifiedWithoutEditingHolder;
  private VirtualFileHolder myIgnoredFilesHolder;
  private VirtualFileHolder myLockedFoldersHolder;
  private DeletedFilesHolder myDeletedFilesHolder = new DeletedFilesHolder();
  private SwitchedFileHolder mySwitchedFilesHolder;
  private final List<LocalChangeList> myChangeLists = new ArrayList<LocalChangeList>();
  private VcsException myUpdateException = null;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private LocalChangeListImpl myDefaultChangelist;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);

  private final Object myPendingUpdatesLock = new Object();
  private boolean myUpdateInProgress = false;
  private VcsDirtyScope myCurrentlyUpdatingScope = null;

  @NonNls private static final String NODE_LIST = "list";
  @NonNls private static final String NODE_IGNORED = "ignored";
  @NonNls private static final String ATT_NAME = "name";
  @NonNls private static final String ATT_COMMENT = "comment";
  @NonNls private static final String NODE_CHANGE = "change";
  @NonNls private static final String ATT_DEFAULT = "default";
  @NonNls private static final String ATT_READONLY = "readonly";
  @NonNls private static final String ATT_VALUE_TRUE = "true";
  @NonNls private static final String ATT_CHANGE_TYPE = "type";
  @NonNls private static final String ATT_CHANGE_BEFORE_PATH = "beforePath";
  @NonNls private static final String ATT_CHANGE_AFTER_PATH = "afterPath";
  @NonNls private static final String ATT_PATH = "path";
  @NonNls private static final String ATT_MASK = "mask";
  private List<CommitExecutor> myExecutors = new ArrayList<CommitExecutor>();
  private final List<IgnoredFileBean> myFilesToIgnore = new ArrayList<IgnoredFileBean>();
  private ProgressIndicator myUpdateChangesProgressIndicator;

  public static final Key<Object> DOCUMENT_BEING_COMMITTED_KEY = new Key<Object>("DOCUMENT_BEING_COMMITTED");

  public static final Topic<LocalChangeListsLoadedListener> LISTS_LOADED = new Topic<LocalChangeListsLoadedListener>(
    "LOCAL_CHANGE_LISTS_LOADED", LocalChangeListsLoadedListener.class);

  private VcsListener myVcsListener = new VcsListener() {
    public void directoryMappingChanged() {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      scheduleUpdate();
    }
  };

  public static ChangeListManagerImpl getInstanceImpl(final Project project) {
    return (ChangeListManagerImpl) project.getComponent(ChangeListManager.class);
  }

  public ChangeListManagerImpl(final Project project) {
    myProject = project;
    myUnversionedFilesHolder = new VirtualFileHolder(project);
    myModifiedWithoutEditingHolder = new VirtualFileHolder(project);
    myIgnoredFilesHolder = new VirtualFileHolder(project);
    myLockedFoldersHolder = new VirtualFileHolder(project);
    mySwitchedFilesHolder = new SwitchedFileHolder(project);

    myWaitingUpdateCompletionQueue = new ArrayList<Runnable>();
  }

  public void projectOpened() {
    initializeForNewProject();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myInitialized = true;
      ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myInitialized = true;
          broadcastStateAfterLoad();
          ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
        }
      });
    }
  }

  private void broadcastStateAfterLoad() {
    synchronized (myChangeLists) {
      if (! myChangeLists.isEmpty()) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(LISTS_LOADED).processLoadedLists(myChangeLists);
      }
    }
  }

  private void initializeForNewProject() {
    if (myChangeLists.isEmpty()) {
      final LocalChangeList list = LocalChangeList.createEmptyChangeList(myProject, VcsBundle.message("changes.default.changlist.name"));
      myChangeLists.add(list);
      setDefaultChangeList(list);

      if (myFilesToIgnore.size() == 0) {
        final String name = myProject.getName();
        myFilesToIgnore.add(IgnoredFileBean.withPath(name + ".iws"));
        myFilesToIgnore.add(IgnoredFileBean.withPath(Project.DIRECTORY_STORE_FOLDER + "/workspace.xml"));
      }
    }
  }

  public void projectClosed() {
    ProjectLevelVcsManager.getInstance(myProject).removeVcsListener(myVcsListener);
    if (myUpdateChangesProgressIndicator != null) {
      myUpdateChangesProgressIndicator.cancel();
    }

    synchronized(myPendingUpdatesLock) {
      myDisposed = true;
      cancelUpdates();
      if (! myWaitingUpdateCompletionQueue.isEmpty()) {
        for (Runnable runnable : myWaitingUpdateCompletionQueue) {
          runnable.run();
        }
        waitForUpdateDone(null);
      }
    }
  }

  private void cancelUpdates() {
    if (myCurrentUpdate != null) {
      myCurrentUpdate.cancel(false);
      myCurrentUpdate = null;
    }
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "ChangeListManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  /**
   * update itself might produce actions done on AWT thread (invoked-after),
   * so waiting for its completion on AWT thread is not good
   *
   * runnable is invoked on AWT thread
   */
  public void invokeAfterUpdate(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title) {
    if (!myInitialized) return;

    final Runnable runnable;
    if (silently) {
      runnable = new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              afterUpdate.run();
              ChangesViewManager.getInstance(myProject).refreshView();
            }
          });
        }
      };
    } else {
      final FictiveBackgroundable fictiveBackgroundable = new FictiveBackgroundable(myProject, afterUpdate, cancellable, title);
      ProgressManager.getInstance().run(fictiveBackgroundable);
      runnable = new Runnable() {
        public void run() {
          fictiveBackgroundable.done();
        }
      };
    }

    synchronized (myPendingUpdatesLock) {
      scheduleUpdate(10, true, runnable);
    }
  }

  private static class FictiveBackgroundable extends Task.Backgroundable {
    private final Runnable myRunnable;
    private boolean myDone;
    private final Object myLock = new Object();

    private FictiveBackgroundable(@Nullable final Project project, @NotNull final Runnable runnable, final boolean cancellable, final String title) {
      super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable, new PerformInBackgroundOption() {
        public boolean shouldStartInBackground() {
          return true;
        }
        public void processSentToBackground() {
        }

        public void processRestoredToForeground() {
        }
      });
      myRunnable = runnable;
      myDone = false;
    }

    public void run(final ProgressIndicator indicator) {
      synchronized (myLock) {
        while ((! myDone) && (! ProgressManager.getInstance().getProgressIndicator().isCanceled())) {
          try {
            myLock.wait();
          }
          catch (InterruptedException e) {
            // ok
          }
        }
      }
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          synchronized (myLock) {
            if (! myDone) {
              return;
            }
          }
          myRunnable.run();
          ChangesViewManager.getInstance(myProject).refreshView();
        }
      });
    }

    public void done() {
      synchronized (myLock) {
        myDone = true;
        myLock.notifyAll();
      }
    }
  }

  public boolean ensureUpToDate(boolean canBeCanceled) {
    if (!myInitialized) return true;

    final boolean ok = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(VcsBundle.message("commit.wait.util.synced.message"));
        }

        synchronized (myPendingUpdatesLock) {
          scheduleUpdate(10, true, null);
          waitForUpdateDone(indicator);
        }
      }
    }, VcsBundle.message("commit.wait.util.synced.title"), canBeCanceled, myProject);

    if (ok) {
      ChangesViewManager.getInstance(myProject).refreshView();
    }

    return ok;
  }

  private void waitForUpdateDone(@Nullable final ProgressIndicator indicator) {
    while (myCurrentUpdate != null && !myCurrentUpdate.isDone() || myUpdateInProgress) {
      if (indicator != null && indicator.isCanceled()) break;

      try {
        myPendingUpdatesLock.wait(100);
      }
      catch (InterruptedException e) {
        break;
      }
    }
  }

  private static class DisposedException extends RuntimeException {}

  private void scheduleUpdate(int millis, final boolean updateUnversionedFiles, final Runnable waiter) {
    final Runnable actualUpdate = new Runnable() {
      public void run() {
        synchronized (myPendingUpdatesLock) {
          if (myDisposed) return;
        }
        if ((! myInitialized) || (ProjectLevelVcsManager.getInstance(myProject).isBackgroundVcsOperationRunning())) {
          scheduleUpdate();
          return;
        }

        updateImmediately(updateUnversionedFiles);
      }
    };

    // synchronized: to do NOT lost future tasks kept in myCurrentUpdate (two threads entered simultaneosly, did cancel, put
    // new value into myCurrentUpdate twice - one task is scheduled but reference lost)
    synchronized (myPendingUpdatesLock) {
      if (myDisposed) {
        // cancel updates scheduling
        if (waiter != null) {
          invokeWaiter(waiter);
        }
        return;
      }
      cancelUpdates();

      // before scheduling the action, put marker
      if (waiter != null) {
        myWaitingUpdateCompletionQueue.add(waiter);
      }

      myCurrentUpdate = ourUpdateAlarm.schedule(actualUpdate, millis, TimeUnit.MILLISECONDS);
    }
  }

  public void scheduleUpdate() {
    scheduleUpdate(300, true, null);
  }

  public void scheduleUpdate(boolean updateUnversionedFiles) {
    scheduleUpdate(300, updateUnversionedFiles, null);
  }

  private void updateImmediately(final boolean updateUnversionedFiles) {
    final Collection<Runnable> updateWaiters = new ArrayList<Runnable>();
    try {
      synchronized (myPendingUpdatesLock) {
        myUpdateInProgress = true;
        updateWaiters.addAll(myWaitingUpdateCompletionQueue);
        checkIfDisposed();
      }

      final VcsDirtyScopeManagerImpl dirtyScopeManager = ((VcsDirtyScopeManagerImpl)VcsDirtyScopeManager.getInstance(myProject));
      final boolean wasEverythingDirty = dirtyScopeManager.isEverythingDirty();
      final List<VcsDirtyScope> scopes = dirtyScopeManager.retrieveScopes();

      final VirtualFileHolder unversionedHolder;
      final VirtualFileHolder modifiedWithoutEditingHolder;
      final VirtualFileHolder ignoredHolder;
      final DeletedFilesHolder deletedHolder;
      final SwitchedFileHolder switchedHolder;
      final VirtualFileHolder lockedFoldersHolder;

      if (wasEverythingDirty) {
        myUpdateException = null;
      }

      if (updateUnversionedFiles) {
        unversionedHolder = myUnversionedFilesHolder.copy();
        deletedHolder = myDeletedFilesHolder.copy();
        modifiedWithoutEditingHolder = myModifiedWithoutEditingHolder.copy();
        ignoredHolder = myIgnoredFilesHolder.copy();
        lockedFoldersHolder = myLockedFoldersHolder.copy();
        switchedHolder = mySwitchedFilesHolder.copy();

        if (wasEverythingDirty) {
          unversionedHolder.cleanAll();
          deletedHolder.cleanAll();
          modifiedWithoutEditingHolder.cleanAll();
          ignoredHolder.cleanAll();
          lockedFoldersHolder.cleanAll();
          switchedHolder.cleanAll();
        }
      }
      else {
        unversionedHolder = myUnversionedFilesHolder;
        deletedHolder = myDeletedFilesHolder;
        modifiedWithoutEditingHolder = myModifiedWithoutEditingHolder;
        ignoredHolder = myIgnoredFilesHolder;
        lockedFoldersHolder = myLockedFoldersHolder;
        switchedHolder = mySwitchedFilesHolder;
      }

      if (wasEverythingDirty) {
        notifyStartProcessingChanges(null);
      }
      for (final VcsDirtyScope scope : scopes) {
        final AbstractVcs vcs = scope.getVcs();
        if (vcs == null) continue;

        myCurrentlyUpdatingScope = scope;
        ChangesViewManager.getInstance(myProject).updateProgressText(VcsBundle.message("changes.update.progress.message", vcs.getDisplayName()), false);
        if (!wasEverythingDirty) {
          notifyStartProcessingChanges(scope);
        }

        if (updateUnversionedFiles && !wasEverythingDirty) {
          unversionedHolder.cleanScope(scope);
          deletedHolder.cleanScope(scope);
          modifiedWithoutEditingHolder.cleanScope(scope);
          ignoredHolder.cleanScope(scope);
          lockedFoldersHolder.cleanScope(scope);
          switchedHolder.cleanScope(scope);
        }

        try {
          final ChangeProvider changeProvider = vcs.getChangeProvider();
          if (changeProvider != null) {
            final FoldersCutDownWorker foldersCutDownWorker = new FoldersCutDownWorker();
            try {
              myUpdateChangesProgressIndicator = new EmptyProgressIndicator() {
                @Override
                public boolean isCanceled() {
                  checkIfDisposed();
                  return false;
                }
              };
              changeProvider.getChanges(scope, new ChangelistBuilder() {
                public void processChange(final Change change) {
                  processChangeInList( change, (ChangeList)null );
                }

                public void processChangeInList(final Change change, final String changeListName) {
                  checkIfDisposed();
                  LocalChangeList list = null;
                  if (changeListName != null) {
                    list = findChangeList(changeListName);
                    if (list == null) {
                      list = addChangeList(changeListName, null);
                    }
                  }
                  processChangeInList(change, list);
                }

                public void processChangeInList(final Change change, final ChangeList changeList) {
                  checkIfDisposed();

                  final String fileName = ChangesUtil.getFilePath(change).getName();
                  if (FileTypeManager.getInstance().isFileIgnored(fileName)) return;

                  ApplicationManager.getApplication().runReadAction(new Runnable() {
                    public void run() {
                      if (isUnder(change, scope)) {
                        try {
                          synchronized (myChangeLists) {
                            if (changeList instanceof LocalChangeListImpl) {
                              ((LocalChangeListImpl) changeList).addChange(change);
                            }
                            else {
                              for (LocalChangeList list : myChangeLists) {
                                if (list == myDefaultChangelist) continue;
                                if (((LocalChangeListImpl) list).processChange(change)) return;
                              }

                              myDefaultChangelist.processChange(change);
                            }
                          }
                        }
                        finally {
                          ChangesViewManager.getInstance(myProject).scheduleRefresh();
                        }
                      }
                    }
                  });
                }

                public void processUnversionedFile(VirtualFile file) {
                  if (file == null || !updateUnversionedFiles) return;
                  checkIfDisposed();
                  if (ExcludedFileIndex.getInstance(myProject).isExcludedFile(file)) return;
                  if (scope.belongsTo(new FilePathImpl(file))) {
                    if (isIgnoredFile(file)) {
                      ignoredHolder.addFile(file);
                    }
                    else {
                      unversionedHolder.addFile(file);
                    }
                    // if a file was previously marked as switched through recursion, remove it from switched list
                    switchedHolder.removeFile(file);
                    ChangesViewManager.getInstance(myProject).scheduleRefresh();
                  }
                }

                public void processLockedFolder(final VirtualFile file) {
                  if (file == null) return;
                  checkIfDisposed();
                  if (scope.belongsTo(new FilePathImpl(file))) {
                    if (foldersCutDownWorker.addCurrent(file)) {
                      lockedFoldersHolder.addFile(file);
                    }
                    ChangesViewManager.getInstance(myProject).scheduleRefresh();
                  }
                }

                public void processLocallyDeletedFile(FilePath file) {
                  if (!updateUnversionedFiles) return;
                  checkIfDisposed();
                  if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
                  if (scope.belongsTo(file)) {
                    deletedHolder.addFile(file);
                    ChangesViewManager.getInstance(myProject).scheduleRefresh();
                  }
                }

                public void processModifiedWithoutCheckout(VirtualFile file) {
                  if (file == null || !updateUnversionedFiles) return;
                  checkIfDisposed();
                  if (ExcludedFileIndex.getInstance(myProject).isExcludedFile(file)) return;
                  if (scope.belongsTo(new FilePathImpl(file))) {
                    modifiedWithoutEditingHolder.addFile(file);
                    ChangesViewManager.getInstance(myProject).scheduleRefresh();
                  }
                }

                public void processIgnoredFile(VirtualFile file) {
                  if (file == null || !updateUnversionedFiles) return;
                  checkIfDisposed();
                  if (ExcludedFileIndex.getInstance(myProject).isExcludedFile(file)) return;
                  if (scope.belongsTo(new FilePathImpl(file))) {
                    ignoredHolder.addFile(file);
                    ChangesViewManager.getInstance(myProject).scheduleRefresh();
                  }
                }

                public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {
                  if (file == null || !updateUnversionedFiles) return;
                  checkIfDisposed();
                  if (ExcludedFileIndex.getInstance(myProject).isExcludedFile(file)) return;
                  if (scope.belongsTo(new FilePathImpl(file))) {
                    switchedHolder.addFile(file, branch, recursive);
                  }
                }

                public boolean isUpdatingUnversionedFiles() {
                  return updateUnversionedFiles;
                }
              }, myUpdateChangesProgressIndicator);
            }
            catch (VcsException e) {
              LOG.info(e);
              if (myUpdateException == null) {
                myUpdateException = e;
              }
            }
            switchedHolder.calculateChildren();
          }
        }
        finally {
          myCurrentlyUpdatingScope = null;
          if (!myDisposed && !wasEverythingDirty) {
            notifyDoneProcessingChanges();
          }
        }
      }
      if (wasEverythingDirty) {
        notifyDoneProcessingChanges();
      }
      if (updateUnversionedFiles) {
        boolean statusChanged = (!myUnversionedFilesHolder.equals(unversionedHolder)) ||
                                (!myDeletedFilesHolder.equals(deletedHolder)) ||
                                (!myModifiedWithoutEditingHolder.equals(modifiedWithoutEditingHolder)) ||
                                (!myIgnoredFilesHolder.equals(ignoredHolder)) ||
                                (!myLockedFoldersHolder.equals(lockedFoldersHolder)) ||
                                (!mySwitchedFilesHolder.equals(switchedHolder));
        myUnversionedFilesHolder = unversionedHolder;
        myDeletedFilesHolder = deletedHolder;
        myModifiedWithoutEditingHolder = modifiedWithoutEditingHolder;
        myIgnoredFilesHolder = ignoredHolder;
        myLockedFoldersHolder = lockedFoldersHolder;
        mySwitchedFilesHolder = switchedHolder;
        if (statusChanged) {
          myListeners.getMulticaster().unchangedFileStatusChanged();
        }
      }
    }
    catch (DisposedException e) {
      // OK, we're finishing all the stuff now.
    }
    catch(ProcessCanceledException e) {
      // OK, we're finishing all the stuff now.
    }
    catch(Exception ex) {
      LOG.error(ex);
    }
    catch(AssertionError ex) {
      LOG.error(ex);
    }
    finally {
      myListeners.getMulticaster().changeListUpdateDone();
      synchronized (myPendingUpdatesLock) {
        myUpdateInProgress = false;

        myWaitingUpdateCompletionQueue.removeAll(updateWaiters);
        
        // this indicates that at least one update action had completed since the request for it
        for (Runnable updateWaiter : updateWaiters) {
          updateWaiter.run();
        }

        myPendingUpdatesLock.notifyAll();
      }
    }
  }

  private void checkIfDisposed() {
    synchronized (myPendingUpdatesLock) {
      if (myDisposed) throw new DisposedException();
    }
  }

  private void notifyStartProcessingChanges(final VcsDirtyScope scope) {
    checkIfDisposed();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (myChangeLists) {
          for (LocalChangeList list : myChangeLists) {
            checkIfDisposed();
            ((LocalChangeListImpl) list).startProcessingChanges(myProject, scope);
          }
        }
      }
    });
  }

  private void notifyDoneProcessingChanges() {
    List<ChangeList> changedLists = new ArrayList<ChangeList>();
    synchronized (myChangeLists) {
      for (LocalChangeList list : myChangeLists) {
        if (((LocalChangeListImpl) list).doneProcessingChanges()) {
          changedLists.add(list);
        }
      }
    }
    for(ChangeList changeList: changedLists) {
      myListeners.getMulticaster().changeListChanged(changeList);
    }
  }

  private static boolean isUnder(final Change change, final VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  public List<LocalChangeList> getChangeListsCopy() {
    synchronized (myChangeLists) {
      List<LocalChangeList> copy = new ArrayList<LocalChangeList>(myChangeLists.size());
      for (LocalChangeList list : myChangeLists) {
        copy.add(list.clone());
      }
      return copy;
    }
  }

  @NotNull
  public List<LocalChangeList> getChangeLists() {
    synchronized (myChangeLists) {
      return myChangeLists;
    }
  }

  public List<File> getAffectedPaths() {
    List<File> files = new ArrayList<File>();
    for (ChangeList list : myChangeLists) {
      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        File beforeFile = null;
        ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null) {
          beforeFile = beforeRevision.getFile().getIOFile();
        }

        if (beforeFile != null) {
          files.add(beforeFile);
        }

        ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
          final File afterFile = afterRevision.getFile().getIOFile();
          if (!afterFile.equals(beforeFile)) {
            files.add(afterFile);
          }
        }
      }
    }

    return files;
  }


  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (ChangeList list : myChangeLists) {
      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
          final VirtualFile vFile = afterRevision.getFile().getVirtualFile();
          if (vFile != null) {
            files.add(vFile);
          }
        }
      }
    }
    return files;
  }

  List<VirtualFile> getUnversionedFiles() {
    return new ArrayList<VirtualFile>(myUnversionedFilesHolder.getFiles());
  }

  List<VirtualFile> getModifiedWithoutEditing() {
    return new ArrayList<VirtualFile>(myModifiedWithoutEditingHolder.getFiles());
  }

  List<VirtualFile> getIgnoredFiles() {
    return new ArrayList<VirtualFile>(myIgnoredFilesHolder.getFiles());
  }

  List<VirtualFile> getLockedFolders() {
    return new ArrayList<VirtualFile>(myLockedFoldersHolder.getFiles());
  }

  List<FilePath> getDeletedFiles() {
    return new ArrayList<FilePath>(myDeletedFilesHolder.getFiles());
  }

  MultiMap<String, VirtualFile> getSwitchedFilesMap() {
    return mySwitchedFilesHolder.getBranchToFileMap();
  }

  VcsException getUpdateException() {
    return myUpdateException;
  }

  public boolean isFileAffected(final VirtualFile file) {
    synchronized (myChangeLists) {
      for (ChangeList list : myChangeLists) {
        for (Change change : list.getChanges()) {
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision != null && afterRevision.getFile().getVirtualFile() == file) return true;
        }
      }
    }

    return myUnversionedFilesHolder.containsFile(file);
  }

  @Nullable
  public LocalChangeList findChangeList(final String name) {
    LocalChangeList result = null;
    final List<LocalChangeList> changeLists = getChangeLists();
    for(LocalChangeList changeList: changeLists) {
      if (changeList.getName().equals(name)) {
        result = changeList;
      }
    }
    return result;
  }

  public LocalChangeList addChangeList(@NotNull String name, final String comment) {
    LOG.assertTrue(findChangeList(name) == null, "Attempt to create duplicate changelist " + name);
    final LocalChangeListImpl list = LocalChangeListImpl.createEmptyChangeListImpl(myProject, name);
    list.setComment(comment);
    synchronized (myChangeLists) {
      myChangeLists.add(list);
    }
    myListeners.getMulticaster().changeListAdded(list);

    // handle changelists created during the update process
    if (myCurrentlyUpdatingScope != null) {
      list.startProcessingChanges(myProject, myCurrentlyUpdatingScope);
    }
    return list;
  }

  public void removeChangeList(LocalChangeList list) {
    Collection<Change> changes;
    LocalChangeListImpl realList = findRealByCopy(list);
    synchronized (myChangeLists) {
      if (realList.isDefault()) throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));

      changes = realList.getChanges();
      for (Change change : changes) {
        myDefaultChangelist.addChange(change);
      }
      myChangeLists.remove(realList);
    }
    myListeners.getMulticaster().changesMoved(changes, realList, myDefaultChangelist);
    myListeners.getMulticaster().changeListRemoved(realList);
  }

  @NotNull
  public Runnable prepareForChangeDeletion(final Collection<Change> changes) {
    final Map<String, LocalChangeList> lists = new HashMap<String, LocalChangeList>();
    final Map<String, List<Change>> map = new HashMap<String, List<Change>>();
    synchronized (myChangeLists) {
      for (Change change : changes) {
        LocalChangeList changeList = getChangeList(change);
        if (changeList == null) {
          changeList = myDefaultChangelist;
        }
        List<Change> list = map.get(changeList.getName());
        if (list == null) {
          list = new ArrayList<Change>();
          map.put(changeList.getName(), list);
          lists.put(changeList.getName(), changeList);
        }
        list.add(change);
      }
    }
    return new Runnable() {
      public void run() {
        final ChangeListListener multicaster = myListeners.getMulticaster();
        synchronized (myChangeLists) {
          for (Map.Entry<String, List<Change>> entry : map.entrySet()) {
            final List<Change> changes = entry.getValue();
            for (Iterator<Change> iterator = changes.iterator(); iterator.hasNext();) {
              final Change change = iterator.next();
              if (getChangeList(change) != null) {
                // was not actually rolled back
                iterator.remove();
              }
            }
            multicaster.changesRemoved(changes, lists.get(entry.getKey()));
          }
        }
      }
    };
  }

  public void setDefaultChangeList(@NotNull LocalChangeList list) {
    final LocalChangeListImpl previousDefault = myDefaultChangelist;
    synchronized (myChangeLists) {
      if (myDefaultChangelist != null) myDefaultChangelist.setDefault(false);
      LocalChangeListImpl realList = findRealByCopy(list);
      realList.setDefault(true);
      myDefaultChangelist = realList;
    }
    myListeners.getMulticaster().defaultListChanged(previousDefault, list);
  }

  public LocalChangeList getDefaultChangeList() {
    return myDefaultChangelist;
  }

  private LocalChangeListImpl findRealByCopy(LocalChangeList list) {
    for (LocalChangeList changeList : myChangeLists) {
      if (changeList.equals(list)) {
        return (LocalChangeListImpl) changeList;
      }
    }
    return (LocalChangeListImpl) list;
  }

  public LocalChangeList getChangeList(Change change) {
    synchronized (myChangeLists) {
      for (LocalChangeList list : myChangeLists) {
        if (list.getChanges().contains(change)) return list;
      }
      return null;
    }
  }

  @Nullable
  public LocalChangeList getIdentityChangeList(Change change) {
    synchronized (myChangeLists) {
      for (LocalChangeList list : myChangeLists) {
        for(Change oldChange: list.getChanges()) {
          if (oldChange == change) {
            return list;
          }
        }
      }
      return null;
    }
  }

  @Nullable
  public Change getChange(VirtualFile file) {
    synchronized (myChangeLists) {
      for (ChangeList list : myChangeLists) {
        for (Change change : list.getChanges()) {
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision != null) {
            String revisionPath = FileUtil.toSystemIndependentName(afterRevision.getFile().getIOFile().getPath());
            if (FileUtil.pathsEqual(revisionPath, file.getPath())) return change;
          }
          final ContentRevision beforeRevision = change.getBeforeRevision();
          if (beforeRevision != null) {
            String revisionPath = FileUtil.toSystemIndependentName(beforeRevision.getFile().getIOFile().getPath());
            if (FileUtil.pathsEqual(revisionPath, file.getPath())) return change;
          }
        }
      }

      return null;
    }
  }

  @Nullable
  public Change getChange(final FilePath file) {
    synchronized (myChangeLists) {
      for (ChangeList list : myChangeLists) {
        for (Change change : list.getChanges()) {
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision != null && afterRevision.getFile().equals(file)) {
            return change;
          }
          final ContentRevision beforeRevision = change.getBeforeRevision();
          if (beforeRevision != null && beforeRevision.getFile().equals(file)) {
            return change;
          }
        }
      }

      return null;
    }
  }

  public boolean isUnversioned(VirtualFile file) {
    return myUnversionedFilesHolder.containsFile(file);
  }

  @NotNull
  public FileStatus getStatus(VirtualFile file) {
    if (myUnversionedFilesHolder.containsFile(file)) return FileStatus.UNKNOWN;
    if (myModifiedWithoutEditingHolder.containsFile(file)) return FileStatus.HIJACKED;
    if (myIgnoredFilesHolder.containsFile(file)) return FileStatus.IGNORED;
    final Change change = getChange(file);
    if (change != null) {
      // moved/renamed dir, both old and new paths are present in filesystem - return "deleted" status for old path
      final FilePath beforePath = ChangesUtil.getBeforePath(change);
      final FilePath afterPath = ChangesUtil.getAfterPath(change);
      if (afterPath != null && beforePath != null && !beforePath.equals(afterPath)) {
        String revisionPath = FileUtil.toSystemIndependentName(beforePath.getPath());
        if (FileUtil.pathsEqual(revisionPath, file.getPath())) {
          return FileStatus.DELETED;
        }
      }
      return change.getFileStatus();
    }
    if (mySwitchedFilesHolder.containsFile(file)) return FileStatus.SWITCHED;
    return FileStatus.NOT_CHANGED;
  }

  @NotNull
  public Collection<Change> getChangesIn(VirtualFile dir) {
    return getChangesIn(new FilePathImpl(dir));
  }

  @NotNull
  public Collection<Change> getChangesIn(final FilePath dirPath) {
    synchronized (myChangeLists) {
      List<Change> changes = new ArrayList<Change>();
      for (ChangeList list : myChangeLists) {
        for (Change change : list.getChanges()) {
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision != null && afterRevision.getFile().isUnder(dirPath, false)) {
            changes.add(change);
            continue;
          }

          final ContentRevision beforeRevision = change.getBeforeRevision();
          if (beforeRevision != null && beforeRevision.getFile().isUnder(dirPath, false)) {
            changes.add(change);
          }
        }
      }

      return changes;
    }
  }

  public void moveChangesTo(LocalChangeList list, final Change[] changes) {
    MultiMap<LocalChangeList, Change> map = new MultiMap<LocalChangeList, Change>();
    synchronized (myChangeLists) {
      LocalChangeListImpl realList = findRealByCopy(list);

      for (LocalChangeList existingList : myChangeLists) {
        for (Change change : changes) {
          if (((LocalChangeListImpl) existingList).removeChange(change)) {
            realList.addChange(change);
            map.putValue(existingList, change);
          }
        }
      }
    }
    for(LocalChangeList fromList: map.keySet()) {
      final Collection<Change> changesInList = map.get(fromList);
      myListeners.getMulticaster().changesMoved(changesInList, fromList, list);
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        ChangesViewManager.getInstance(myProject).refreshView();
      }
    });
  }

  public void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> files) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    ChangesUtil.processVirtualFilesByVcs(myProject, files, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final CheckinEnvironment environment = vcs.getCheckinEnvironment();
        if (environment != null) {
          final List<VcsException> result = environment.scheduleUnversionedFilesForAddition(items);
          if (result != null) {
            exceptions.addAll(result);
          }
        }
      }
    });

    if (exceptions.size() > 0) {
      StringBuilder message = new StringBuilder(VcsBundle.message("error.adding.files.prompt"));
      for(VcsException ex: exceptions) {
        message.append("\n").append(ex.getMessage());
      }
      Messages.showErrorDialog(myProject, message.toString(), VcsBundle.message("error.adding.files.title"));
    }

    for (VirtualFile file : files) {
      VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
      FileStatusManager.getInstance(myProject).fileStatusChanged(file);
    }

    if (!list.isDefault()) {
      // find the changes for the added files and move them to the necessary changelist
      invokeAfterUpdate(new Runnable() {
        public void run() {
          List<Change> changesToMove = new ArrayList<Change>();
          for(Change change: getDefaultChangeList().getChanges()) {
            final ContentRevision afterRevision = change.getAfterRevision();
            if (afterRevision != null) {
              VirtualFile vFile = afterRevision.getFile().getVirtualFile();
              if (files.contains(vFile)) {
                changesToMove.add(change);
              }
            }
          }

          if (changesToMove.size() > 0) {
            moveChangesTo(list, changesToMove.toArray(new Change[changesToMove.size()]));
          }

          ChangesViewManager.getInstance(myProject).scheduleRefresh();
        }
      }, false, false, VcsBundle.message("change.lists.manager.add.unversioned"));
    } else {
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
    }
  }

  public Project getProject() {
    return myProject;
  }

  public void addChangeListListener(ChangeListListener listener) {
    myListeners.addListener(listener);
  }


  public void removeChangeListListener(ChangeListListener listener) {
    myListeners.removeListener(listener);
  }

  public void registerCommitExecutor(CommitExecutor executor) {
    myExecutors.add(executor);
  }

  public void commitChanges(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, false);
  }

  private boolean doCommit(final LocalChangeList changeList, final List<Change> changes, final boolean synchronously) {
    return new CommitHelper(myProject, changeList, changes, changeList.getName(),
                     changeList.getComment(), new ArrayList<CheckinHandler>(), false, synchronously).doCommit();
  }

  public void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, true);
  }

  public boolean commitChangesSynchronouslyWithResult(final LocalChangeList changeList, final List<Change> changes) {
    return doCommit(changeList, changes, true);
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(Element element) throws InvalidDataException {
    final List<Element> listNodes = (List<Element>)element.getChildren(NODE_LIST);
    for (Element listNode : listNodes) {
      readChangeList(listNode);
    }
    myFilesToIgnore.clear();
    final List<Element> ignoredNodes = (List<Element>)element.getChildren(NODE_IGNORED);
    for (Element ignoredNode: ignoredNodes) {
      readFileToIgnore(ignoredNode);
    }

    if (myChangeLists.size() > 0 && myDefaultChangelist == null) {
      setDefaultChangeList(myChangeLists.get(0));
    }
  }

  private void readChangeList(final Element listNode) {
    // workaround for loading incorrect settings (with duplicate changelist names)
    final String changeListName = listNode.getAttributeValue(ATT_NAME);
    LocalChangeList list = findChangeList(changeListName);
    if (list == null) {
      list = addChangeList(changeListName, listNode.getAttributeValue(ATT_COMMENT));
    }
    //noinspection unchecked
    final List<Element> changeNodes = (List<Element>)listNode.getChildren(NODE_CHANGE);
    for (Element changeNode : changeNodes) {
      try {
        ((LocalChangeListImpl) list).addChange(readChange(changeNode));
      }
      catch (OutdatedFakeRevisionException e) {
        // Do nothing. Just skip adding outdated revisions to the list.
      }
    }

    if (ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_DEFAULT))) {
      setDefaultChangeList(list);
    }
    if (ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_READONLY))) {
      list.setReadOnly(true);
    }
  }

  private void readFileToIgnore(final Element ignoredNode) {
    IgnoredFileBean bean = new IgnoredFileBean();
    String path = ignoredNode.getAttributeValue(ATT_PATH);
    if (path != null) {
      bean.setPath(path);
    }
    String mask = ignoredNode.getAttributeValue(ATT_MASK);
    if (mask != null) {
      bean.setMask(mask);
    }
    myFilesToIgnore.add(bean);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    synchronized (myChangeLists) {
      for (LocalChangeList list : myChangeLists) {
        Element listNode = new Element(NODE_LIST);
        element.addContent(listNode);
        if (list.isDefault()) {
          listNode.setAttribute(ATT_DEFAULT, ATT_VALUE_TRUE);
        }
        if (list.isReadOnly()) {
          listNode.setAttribute(ATT_READONLY, ATT_VALUE_TRUE);
        }

        listNode.setAttribute(ATT_NAME, list.getName());
        listNode.setAttribute(ATT_COMMENT, list.getComment());
        for (Change change : list.getChanges()) {
          writeChange(listNode, change);
        }
      }
    }
    synchronized(myFilesToIgnore) {
      for(IgnoredFileBean bean: myFilesToIgnore) {
        Element fileNode = new Element(NODE_IGNORED);
        element.addContent(fileNode);
        String path = bean.getPath();
        if (path != null) {
          fileNode.setAttribute("path", path);
        }
        String mask = bean.getMask();
        if (mask != null) {
          fileNode.setAttribute("mask", mask);
        }
      }
    }
  }

  private static void writeChange(final Element listNode, final Change change) {
    Element changeNode = new Element(NODE_CHANGE);
    listNode.addContent(changeNode);
    changeNode.setAttribute(ATT_CHANGE_TYPE, change.getType().name());

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    changeNode.setAttribute(ATT_CHANGE_BEFORE_PATH, bRev != null ? bRev.getFile().getPath() : "");
    changeNode.setAttribute(ATT_CHANGE_AFTER_PATH, aRev != null ? aRev.getFile().getPath() : "");
  }

  private static Change readChange(Element changeNode) throws OutdatedFakeRevisionException {
    String bRev = changeNode.getAttributeValue(ATT_CHANGE_BEFORE_PATH);
    String aRev = changeNode.getAttributeValue(ATT_CHANGE_AFTER_PATH);
    return new Change(StringUtil.isEmpty(bRev) ? null : new FakeRevision(bRev), StringUtil.isEmpty(aRev) ? null : new FakeRevision(aRev));
  }

  private static final class OutdatedFakeRevisionException extends Exception {}

  public static class FakeRevision implements ContentRevision {
    private final FilePath myFile;

    public FakeRevision(String path) throws OutdatedFakeRevisionException {
      final FilePath file = VcsContextFactory.SERVICE.getInstance().createFilePathOn(new File(path));
      if (file == null) throw new OutdatedFakeRevisionException();
      myFile = file;
    }

    @Nullable
    public String getContent() { return null; }

    @NotNull
    public FilePath getFile() {
      return myFile;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return VcsRevisionNumber.NULL;
    }
  }


  public void reopenFiles(List<FilePath> paths) {
    final ReadonlyStatusHandlerImpl readonlyStatusHandler = (ReadonlyStatusHandlerImpl)ReadonlyStatusHandlerImpl.getInstance(myProject);
    final boolean savedOption = readonlyStatusHandler.getState().SHOW_DIALOG;
    readonlyStatusHandler.getState().SHOW_DIALOG = false;
    try {
      readonlyStatusHandler.ensureFilesWritable(collectFiles(paths));
    }
    finally {
      readonlyStatusHandler.getState().SHOW_DIALOG = savedOption;
    }
  }

  public List<CommitExecutor> getRegisteredExecutors() {
    return Collections.unmodifiableList(myExecutors);
  }

  public void addFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
    updateIgnoredFiles();
  }

  public void setFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      myFilesToIgnore.clear();
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
    updateIgnoredFiles();
  }

  private void updateIgnoredFiles() {
    List<VirtualFile> unversionedFiles = myUnversionedFilesHolder.getFiles();
    List<VirtualFile> ignoredFiles = myIgnoredFilesHolder.getFiles();
    for(VirtualFile file: unversionedFiles) {
      if (isIgnoredFile(file)) {
        myUnversionedFilesHolder.removeFile(file);
        myIgnoredFilesHolder.addFile(file);
      }
    }
    for(VirtualFile file: ignoredFiles) {
      if (!isIgnoredFile(file)) {
        // the file may have been reported as ignored by the VCS, so we can't directly move it to unversioned files
        VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
      }
    }
    FileStatusManager.getInstance(getProject()).fileStatusesChanged();
    ChangesViewManager.getInstance(myProject).scheduleRefresh();
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    synchronized(myFilesToIgnore) {
      return myFilesToIgnore.toArray(new IgnoredFileBean[myFilesToIgnore.size()]);
    }
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    synchronized(myFilesToIgnore) {
      if (myFilesToIgnore.size() == 0) {
        return false;
      }
      String filePath = null;
      // don't use VfsUtil.getRelativePath() here because it can't handle paths where one file is not a direct ancestor of another one
      final VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        filePath = FileUtil.getRelativePath(new File(baseDir.getPath()), new File(file.getPath()));
        if (filePath != null) {
          filePath = FileUtil.toSystemIndependentName(filePath);
        }
        if (file.isDirectory()) {
          filePath += "/";
        }
      }
      for(IgnoredFileBean bean: myFilesToIgnore) {
        if (filePath != null) {
          final String prefix = bean.getPath();
          if ("./".equals(prefix)) {
            // special case for ignoring the project base dir (IDEADEV-16056)
            final String basePath = FileUtil.toSystemIndependentName(baseDir.getPath());
            final String fileAbsPath = FileUtil.toSystemIndependentName(file.getPath());
            if (StringUtil.startsWithIgnoreCase(fileAbsPath, basePath)) {
              return true;
            }
          }
          else if (prefix != null && StringUtil.startsWithIgnoreCase(filePath, FileUtil.toSystemIndependentName(prefix))) {
            return true;
          }
        }
        final Pattern pattern = bean.getPattern();
        if (pattern != null && pattern.matcher(file.getName()).matches()) {
          return true;
        }
      }
      return false;
    }
  }

  @Nullable
  public String getSwitchedBranch(final VirtualFile file) {
    return mySwitchedFilesHolder.getBranchForFile(file);    
  }

  private static VirtualFile[] collectFiles(final List<FilePath> paths) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (FilePath path : paths) {
      if (path.getVirtualFile() != null) {
        result.add(path.getVirtualFile());
      }
    }
    
    return result.toArray(new VirtualFile[result.size()]); 
  }

  public void notifyChangeListRenamed(final LocalChangeList list, final String oldName) {
    myListeners.getMulticaster().changeListRenamed(list, oldName);
  }

  public void notifyChangeListCommentChanged(final LocalChangeList list, final String oldComment) {
    myListeners.getMulticaster().changeListCommentChanged(list, oldComment);
  }

  private void invokeWaiter(final Runnable waiter) {
    ApplicationManager.getApplication().invokeLater(waiter);
  }
}
