package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManager implements ProjectComponent, ChangeListOwner, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListManagerImpl");

  private Project myProject;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private static ScheduledExecutorService ourUpdateAlarm = Executors.newSingleThreadScheduledExecutor();

  private ScheduledFuture<?> myCurrentUpdate = null;

  private boolean myInitialized = false;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private boolean myDisposed = false;

  private UnversionedFilesHolder myUnversionedFilesHolder;
  private DeletedFilesHolder myDeletedFilesHolder = new DeletedFilesHolder();
  private final List<LocalChangeList> myChangeLists = new ArrayList<LocalChangeList>();

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private LocalChangeList myDefaultChangelist;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);

  private final Object myPendingUpdatesLock = new Object();
  private boolean myUpdateInProgress = false;
  private VcsDirtyScope myCurrentlyUpdatingScope = null;

  @NonNls private static final String NODE_LIST = "list";
  @NonNls private static final String ATT_NAME = "name";
  @NonNls private static final String ATT_COMMENT = "comment";
  @NonNls private static final String NODE_CHANGE = "change";
  @NonNls private static final String ATT_DEFAULT = "default";
  @NonNls private static final String ATT_READONLY = "readonly";
  @NonNls private static final String ATT_VALUE_TRUE = "true";
  @NonNls private static final String ATT_CHANGE_TYPE = "type";
  @NonNls private static final String ATT_CHANGE_BEFORE_PATH = "beforePath";
  @NonNls private static final String ATT_CHANGE_AFTER_PATH = "afterPath";
  private List<CommitExecutor> myExecutors = new ArrayList<CommitExecutor>();

  public static final Key<Object> DOCUMENT_BEING_COMMITTED_KEY = new Key<Object>("DOCUMENT_BEING_COMMITTED");

  private VcsListener myVcsListener = new VcsListener() {
    public void moduleVcsChanged(Module module, @Nullable AbstractVcs newVcs) {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      scheduleUpdate();
    }
  };

  public static ChangeListManagerImpl getInstanceImpl(final Project project) {
    return (ChangeListManagerImpl) project.getComponent(ChangeListManager.class);
  }

  public ChangeListManagerImpl(final Project project) {
    myProject = project;
    myUnversionedFilesHolder = new UnversionedFilesHolder(project);
  }

  public void projectOpened() {
    createDefaultChangelistIfNecessary();

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myInitialized = true;
        ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
      }
    });
  }

  private void createDefaultChangelistIfNecessary() {
    if (myChangeLists.isEmpty()) {
      final LocalChangeList list = LocalChangeList.createEmptyChangeList(VcsBundle.message("changes.default.changlist.name"));
      myChangeLists.add(list);
      setDefaultChangeList(list);
    }
  }

  public void projectClosed() {
    myDisposed = true;
    ProjectLevelVcsManager.getInstance(myProject).removeVcsListener(myVcsListener);
    cancelUpdates();
    synchronized(myPendingUpdatesLock) {
      waitForUpdateDone(null);
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

  public boolean ensureUpToDate(boolean canBeCanceled) {
    if (!myInitialized) return true;

    final boolean ok = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(VcsBundle.message("commit.wait.util.synced.message"));
        }

        synchronized (myPendingUpdatesLock) {
          scheduleUpdate(10, true);
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

  private void scheduleUpdate(int millis, final boolean updateUnversionedFiles) {
    cancelUpdates();
    myCurrentUpdate = ourUpdateAlarm.schedule(new Runnable() {
      public void run() {
        if (myDisposed) return;
        if (!myInitialized) {
          scheduleUpdate();
          return;
        }

        updateImmediately(updateUnversionedFiles);
      }
    }, millis, TimeUnit.MILLISECONDS);
  }

  public void scheduleUpdate() {
    scheduleUpdate(300, true);
  }

  public void scheduleUpdate(boolean updateUnversionedFiles) {
    scheduleUpdate(300, updateUnversionedFiles);
  }

  private void updateImmediately(final boolean updateUnversionedFiles) {
    try {
      synchronized (myPendingUpdatesLock) {
        myUpdateInProgress = true;
      }

      if (myDisposed) throw new DisposedException();

      final VcsDirtyScopeManagerImpl dirtyScopeManager = ((VcsDirtyScopeManagerImpl)VcsDirtyScopeManager.getInstance(myProject));
      final boolean wasEverythingDirty = dirtyScopeManager.isEverythingDirty();
      final List<VcsDirtyScope> scopes = dirtyScopeManager.retreiveScopes();

      final UnversionedFilesHolder unversionedHolder;
      final DeletedFilesHolder deletedHolder;

      if (updateUnversionedFiles) {
        unversionedHolder = myUnversionedFilesHolder.copy();
        deletedHolder = myDeletedFilesHolder.copy();

        if (wasEverythingDirty) {
          unversionedHolder.cleanAll();
          deletedHolder.cleanAll();
        }
      }
      else {
        unversionedHolder = myUnversionedFilesHolder;
        deletedHolder = myDeletedFilesHolder;
      }

      for (final VcsDirtyScope scope : scopes) {
        final AbstractVcs vcs = scope.getVcs();
        if (vcs == null) continue;

        myCurrentlyUpdatingScope = scope;
        ChangesViewManager.getInstance(myProject).updateProgressText(VcsBundle.message("changes.update.progress.message", vcs.getDisplayName()));
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            synchronized (myChangeLists) {
              for (LocalChangeList list : getChangeLists()) {
                if (myDisposed) throw new DisposedException();
                list.startProcessingChanges(scope);
              }
            }
          }
        });

        if (updateUnversionedFiles && !wasEverythingDirty) {
          unversionedHolder.cleanScope(scope);
          deletedHolder.cleanScope(scope);
        }

        try {
          final ChangeProvider changeProvider = vcs.getChangeProvider();
          if (changeProvider != null) {
            changeProvider.getChanges(scope, new ChangelistBuilder() {
              public void processChange(final Change change) {
                processChangeInList(change, null);
              }

              public void processChangeInList(final Change change, final ChangeList changeList) {
                if (myDisposed) throw new DisposedException();

                final String fileName = ChangesUtil.getFilePath(change).getName();
                if (FileTypeManager.getInstance().isFileIgnored(fileName)) return;

                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    if (isUnder(change, scope)) {
                      try {
                        synchronized (myChangeLists) {
                          if (changeList instanceof LocalChangeList) {
                            ((LocalChangeList) changeList).addChange(change);
                          }
                          else {
                            for (LocalChangeList list : myChangeLists) {
                              if (list == myDefaultChangelist) continue;
                              if (list.processChange(change)) return;
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
                if (myDisposed) throw new DisposedException();
                if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
                if (scope.belongsTo(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file))) {
                  unversionedHolder.addFile(file);
                  ChangesViewManager.getInstance(myProject).scheduleRefresh();
                }
              }

              public void processLocallyDeletedFile(FilePath file) {
                if (!updateUnversionedFiles) return;
                if (myDisposed) throw new DisposedException();
                if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
                if (scope.belongsTo(file)) {
                  deletedHolder.addFile(file);
                  ChangesViewManager.getInstance(myProject).scheduleRefresh();
                }
              }


              public void processModifiedWithoutEditing(VirtualFile file) {
                if (myDisposed) throw new DisposedException();
                //TODO:
              }

              public boolean isUpdatingUnversionedFiles() {
                return updateUnversionedFiles;
              }
            }, null); // TODO: make real indicator
          }
        }
        finally {
          myCurrentlyUpdatingScope = null;
          if (!myDisposed) {
            List<ChangeList> changedLists = new ArrayList<ChangeList>();
            synchronized (myChangeLists) {
              for (LocalChangeList list : myChangeLists) {
                if (list.doneProcessingChanges()) {
                  changedLists.add(list);
                }
              }
            }
            for(ChangeList changeList: changedLists) {
              myListeners.getMulticaster().changeListChanged(changeList);
            }
          }
        }

        if (updateUnversionedFiles) {
          myUnversionedFilesHolder = unversionedHolder;
          myDeletedFilesHolder = deletedHolder;
        }
        myListeners.getMulticaster().changeListUpdateDone();
      }
    }
    catch (DisposedException e) {
      // OK, we're finishing all the stuff now.
    }
    catch(Exception ex) {
      LOG.error(ex);
    }
    finally {
      ChangesViewManager.getInstance(myProject).updateProgressText("");
      synchronized (myPendingUpdatesLock) {
        myUpdateInProgress = false;
        myPendingUpdatesLock.notifyAll();
      }
    }
  }

  private static boolean isUnder(final Change change, final VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  List<LocalChangeList> getChangeListsCopy() {
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

  List<FilePath> getDeletedFiles() {
    return new ArrayList<FilePath>(myDeletedFilesHolder.getFiles());
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
    final LocalChangeList list = LocalChangeList.createEmptyChangeList(name);
    list.setComment(comment);
    synchronized (myChangeLists) {
      myChangeLists.add(list);
    }
    myListeners.getMulticaster().changeListAdded(list);

    // handle changelists created during the update process
    if (myCurrentlyUpdatingScope != null) {
      list.startProcessingChanges(myCurrentlyUpdatingScope);
    }
    return list;
  }

  public void removeChangeList(LocalChangeList list) {
    Collection<Change> changes;
    synchronized (myChangeLists) {
      if (list.isDefault()) throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));

      changes = list.getChanges();
      for (Change change : changes) {
        myDefaultChangelist.addChange(change);
      }
    }
    myListeners.getMulticaster().changesMoved(changes, list, myDefaultChangelist);
    synchronized (myChangeLists) {
      myChangeLists.remove(list);
    }
    myListeners.getMulticaster().changeListRemoved(list);
  }

  private void addChangeToList(final Change change, final LocalChangeList list) {
    list.addChange(change);
    myListeners.getMulticaster().changeListChanged(list);
  }

  public void setDefaultChangeList(@NotNull LocalChangeList list) {
    synchronized (myChangeLists) {
      if (myDefaultChangelist != null) myDefaultChangelist.setDefault(false);
      list = findRealByCopy(list);
      list.setDefault(true);
      myDefaultChangelist = list;
    }
    myListeners.getMulticaster().defaultListChanged(list);
  }

  public LocalChangeList getDefaultChangeList() {
    return myDefaultChangelist;
  }

  private LocalChangeList findRealByCopy(LocalChangeList list) {
    for (LocalChangeList changeList : myChangeLists) {
      if (changeList.equals(list)) {
        return changeList;
      }
    }
    return list;
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
  public Change getChange(VirtualFile file) {
    synchronized (myChangeLists) {
      for (ChangeList list : myChangeLists) {
        for (Change change : list.getChanges()) {
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision != null) {
            String revisionPath = FileUtil.toSystemIndependentName(afterRevision.getFile().getPath());
            if (FileUtil.pathsEqual(revisionPath, file.getPath())) return change;
          }
        }
      }

      return null;
    }
  }

  @NotNull
  public FileStatus getStatus(VirtualFile file) {
    if (myUnversionedFilesHolder.containsFile(file)) return FileStatus.UNKNOWN;
    final Change change = getChange(file);
    return change == null ? FileStatus.NOT_CHANGED : change.getFileStatus();
  }

  @NotNull
  public Collection<Change> getChangesIn(VirtualFile dir) {
    return getChangesIn(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir));
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
      list = findRealByCopy(list);

      for (LocalChangeList existingList : getChangeLists()) {
        for (Change change : changes) {
          if (existingList.removeChange(change)) {
            list.addChange(change);
            map.putValue(existingList, change);
          }
        }
      }
    }
    for(LocalChangeList fromList: map.keySet()) {
      final List<Change> changesInList = map.get(fromList);
      myListeners.getMulticaster().changesMoved(changesInList, fromList, list);
    }
  }

  public void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> files) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    ChangesUtil.processVirtualFilesByVcs(myProject, files, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final ChangeProvider provider = vcs.getChangeProvider();
        if (provider != null) {
          exceptions.addAll(provider.scheduleUnversionedFilesForAddition(files));
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
      ensureUpToDate(false);
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
    }

    ChangesViewManager.getInstance(myProject).scheduleRefresh();
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

  private void doCommit(final LocalChangeList changeList, final List<Change> changes, final boolean synchronously) {
    new CommitHelper(myProject, changeList, changes, changeList.getName(),
                     changeList.getComment(), new ArrayList<CheckinHandler>(), false, synchronously).doCommit();
  }

  public void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, true);
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(Element element) throws InvalidDataException {
    final List<Element> listNodes = (List<Element>)element.getChildren(NODE_LIST);
    for (Element listNode : listNodes) {
      LocalChangeList list = addChangeList(listNode.getAttributeValue(ATT_NAME), listNode.getAttributeValue(ATT_COMMENT));
      final List<Element> changeNodes = (List<Element>)listNode.getChildren(NODE_CHANGE);
      for (Element changeNode : changeNodes) {
        try {
          addChangeToList(readChange(changeNode), list);
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

    if (myChangeLists.size() > 0 && myDefaultChangelist == null) {
      setDefaultChangeList(myChangeLists.get(0));
    }
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

  private static class FakeRevision implements ContentRevision {
    private final FilePath myFile;

    public FakeRevision(String path) throws OutdatedFakeRevisionException {
      final FilePath file = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(new File(path));
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
    final boolean savedOption = readonlyStatusHandler.SHOW_DIALOG;
    readonlyStatusHandler.SHOW_DIALOG = false;
    try {
      readonlyStatusHandler.ensureFilesWritable(collectFiles(paths));
    }
    finally {
      readonlyStatusHandler.SHOW_DIALOG = savedOption;
    }
  }

  public List<CommitExecutor> getRegisteredExecutors() {
    return Collections.unmodifiableList(myExecutors);
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
}
