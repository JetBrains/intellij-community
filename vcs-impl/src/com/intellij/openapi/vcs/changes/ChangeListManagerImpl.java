package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
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

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManagerEx implements ProjectComponent, ChangeListOwner, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListManagerImpl");

  private Project myProject;
  private final UpdateRequestsQueue myUpdater;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private static ScheduledExecutorService ourUpdateAlarm = ConcurrencyUtil.newSingleScheduledThreadExecutor("Change List Updater");

  private final Modifier myModifier;

  private FileHolderComposite myComposite;

  private ChangeListWorker myWorker;
  private VcsException myUpdateException = null;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);

  private final Object myDataLock = new Object();

  private List<CommitExecutor> myExecutors = new ArrayList<CommitExecutor>();

  private final IgnoredFilesComponent myIgnoredIdeaLevel;
  private ProgressIndicator myUpdateChangesProgressIndicator;

  public static final Key<Object> DOCUMENT_BEING_COMMITTED_KEY = new Key<Object>("DOCUMENT_BEING_COMMITTED");

  public static final Topic<LocalChangeListsLoadedListener> LISTS_LOADED = new Topic<LocalChangeListsLoadedListener>(
    "LOCAL_CHANGE_LISTS_LOADED", LocalChangeListsLoadedListener.class);

  private VcsListener myVcsListener = new VcsListener() {
    public void directoryMappingChanged() {
      VcsDirtyScopeManager.getInstanceChecked(myProject).markEverythingDirty();
      scheduleUpdate();
    }
  };

  public static ChangeListManagerImpl getInstanceImpl(final Project project) {
    return (ChangeListManagerImpl) project.getComponent(ChangeListManager.class);
  }

  public ChangeListManagerImpl(final Project project) {
    myProject = project;
    myComposite = new FileHolderComposite(project);
    myIgnoredIdeaLevel = new IgnoredFilesComponent(myProject);
    myUpdater = new UpdateRequestsQueue(myProject, ourUpdateAlarm, new ActualUpdater());

    myWorker = new ChangeListWorker(myProject);
    myModifier = new Modifier(myWorker, myListeners);
  }

  public void projectOpened() {
    initializeForNewProject();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myUpdater.initialized();
      ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myUpdater.initialized();
          broadcastStateAfterLoad();
          ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
        }
      });
    }
  }

  private void broadcastStateAfterLoad() {
    synchronized (myDataLock) {
      final List<LocalChangeList> listCopy = getChangeListsCopy();
      if (! listCopy.isEmpty()) {
        myProject.getMessageBus().syncPublisher(LISTS_LOADED).processLoadedLists(listCopy);
      }
    }
  }

  private void initializeForNewProject() {
    synchronized (myDataLock) {
      if (myWorker.isEmpty()) {
        final LocalChangeList list = myWorker.addChangeList(VcsBundle.message("changes.default.changlist.name"), null);
        setDefaultChangeList(list);

        if (myIgnoredIdeaLevel.isEmpty()) {
          final String name = myProject.getName();
          myIgnoredIdeaLevel.add(IgnoredFileBean.withPath(name + ".iws"));
          myIgnoredIdeaLevel.add(IgnoredFileBean.withPath(Project.DIRECTORY_STORE_FOLDER + "/workspace.xml"));
        }
      }
    }
  }

  public void projectClosed() {
    ProjectLevelVcsManager.getInstance(myProject).removeVcsListener(myVcsListener);

    synchronized (myDataLock) {
      if (myUpdateChangesProgressIndicator != null) {
        myUpdateChangesProgressIndicator.cancel();
      }
    }

    myUpdater.stop();
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
  public void invokeAfterUpdate(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title,
                                final boolean synchronous) {
    myUpdater.invokeAfterUpdate(afterUpdate, cancellable, silently, title, synchronous);
  }

  static class DisposedException extends RuntimeException {}

  public void scheduleUpdate() {
    myUpdater.schedule(true);
  }

  public void scheduleUpdate(boolean updateUnversionedFiles) {
    myUpdater.schedule(updateUnversionedFiles);
  }

  private class ActualUpdater implements Consumer<Boolean> {
    public void consume(final Boolean aBoolean) {
      updateImmediately(aBoolean);
    }
  }

  private void updateImmediately(final boolean updateUnversionedFiles) {
    FileHolderComposite composite;
    ChangeListWorker changeListWorker;

    final VcsDirtyScopeManagerImpl dirtyScopeManager;
    try {
      dirtyScopeManager = ((VcsDirtyScopeManagerImpl) VcsDirtyScopeManager.getInstanceChecked(myProject));
    }
    catch(Exception ex) {
      LOG.error(ex);
      return;
    }
    final boolean wasEverythingDirty = dirtyScopeManager.isEverythingDirty();
    final List<VcsDirtyScope> scopes = dirtyScopeManager.retrieveScopes();
    if ((! wasEverythingDirty) && scopes.isEmpty()) {
      return;
    }

    try {
      checkIfDisposed();

      // copy existsing data to objects that would be updated.
      // mark for "modifier" that update started (it would create duplicates of modification commands done by user during update;
      // after update of copies of objects is complete, it would apply the same modifications to copies.)
      synchronized (myDataLock) {
        changeListWorker = myWorker.copy();
        composite = updateUnversionedFiles ? (FileHolderComposite) myComposite.copy() : myComposite;
        myModifier.enterUpdate();
        if (wasEverythingDirty) {
          myUpdateException = null;
        }
        if (updateUnversionedFiles && wasEverythingDirty) {
          composite.cleanAll();         
        }
        if (wasEverythingDirty) {
          changeListWorker.notifyStartProcessingChanges(null);
        }
        ChangesViewManager.getInstance(myProject).scheduleRefresh();
      }

      // do actual requests about file statuses
      final UpdatingChangeListBuilder builder = new UpdatingChangeListBuilder(changeListWorker, composite, new Getter<Boolean>() {
        public Boolean get() {
          return myUpdater.isStopped();
        }
      }, updateUnversionedFiles, myIgnoredIdeaLevel);

      for (final VcsDirtyScope scope : scopes) {
        final AbstractVcs vcs = scope.getVcs();
        if (vcs == null) continue;

        ChangesViewManager.getInstance(myProject).updateProgressText(VcsBundle.message("changes.update.progress.message", vcs.getDisplayName()), false);
        if (! wasEverythingDirty) {
          changeListWorker.notifyStartProcessingChanges(scope);
        }
        if (updateUnversionedFiles && !wasEverythingDirty) {
          composite.cleanScope(scope);
        }
        actualUpdate(wasEverythingDirty, composite, builder, scope, vcs, changeListWorker);
      }
      
      synchronized (myDataLock) {
        // do same modifications to change lists as was done during update + do delayed notifications
        myModifier.exitUpdate();
        myModifier.apply(changeListWorker);
        myModifier.clearQueue();
        // update member from copy
        myWorker.takeData(changeListWorker);

        if (wasEverythingDirty) {
          changeListWorker.notifyDoneProcessingChanges(myListeners);
        }
        if (updateUnversionedFiles) {
          boolean statusChanged = myComposite.equals(composite);
          myComposite = composite;
          if (statusChanged) {
            myListeners.getMulticaster().unchangedFileStatusChanged();
          }
        }

        updateIgnoredFiles(false);
      }
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
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
      synchronized (myDataLock) {
        myListeners.getMulticaster().changeListUpdateDone();
        ChangesViewManager.getInstance(myProject).scheduleRefresh();
      }
    }
  }

  private void actualUpdate(final boolean wasEverythingDirty, final FileHolderComposite composite, final UpdatingChangeListBuilder builder,
                            final VcsDirtyScope scope, final AbstractVcs vcs, final ChangeListWorker changeListWorker) {
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
          builder.setCurrent(scope, foldersCutDownWorker);
          changeProvider.getChanges(scope, builder, myUpdateChangesProgressIndicator);
        }
        catch (VcsException e) {
          LOG.info(e);
          if (myUpdateException == null) {
            myUpdateException = e;
          }
        }
        composite.getSwitchedFileHolder().calculateChildren();
      }
    }
    finally {
      if ((! myUpdater.isStopped()) && !wasEverythingDirty) {
        changeListWorker.notifyDoneProcessingChanges(myListeners);
      }
    }
  }

  private void checkIfDisposed() {
    if (myUpdater.isStopped()) throw new DisposedException();
  }

  static boolean isUnder(final Change change, final VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  public List<LocalChangeList> getChangeListsCopy() {
    synchronized (myDataLock) {
      return myWorker.getListsCopy();
    }
  }

  /**
   * @deprecated 
   * this method made equivalent to {@link #getChangeListsCopy()} so to don't be confused by method name,
   * better use {@link #getChangeListsCopy()}
   */
  @NotNull
  public List<LocalChangeList> getChangeLists() {
    synchronized (myDataLock) {
      return getChangeListsCopy();
    }
  }

  public List<File> getAffectedPaths() {
    synchronized (myDataLock) {
      return myWorker.getAffectedPaths();
    }
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    synchronized (myDataLock) {
      return myWorker.getAffectedFiles();
    }
  }

  List<VirtualFile> getUnversionedFiles() {
    return new ArrayList<VirtualFile>(myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles());
  }

  List<VirtualFile> getModifiedWithoutEditing() {
    return new ArrayList<VirtualFile>(myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).getFiles());
  }

  List<VirtualFile> getIgnoredFiles() {
    return new ArrayList<VirtualFile>(myComposite.getVFHolder(FileHolder.HolderType.IGNORED).getFiles());
  }

  List<VirtualFile> getLockedFolders() {
    return new ArrayList<VirtualFile>(myComposite.getVFHolder(FileHolder.HolderType.LOCKED).getFiles());
  }

  List<FilePath> getDeletedFiles() {
    return new ArrayList<FilePath>(myComposite.getDeletedFilesHolder().getFiles());
  }

  MultiMap<String, VirtualFile> getSwitchedFilesMap() {
    return myComposite.getSwitchedFileHolder().getBranchToFileMap();
  }

  VcsException getUpdateException() {
    return myUpdateException;
  }

  public boolean isFileAffected(final VirtualFile file) {
    synchronized (myDataLock) {
      return myWorker.getStatus(file) != null;
    }
  }

  @Nullable
  public LocalChangeList findChangeList(final String name) {
    synchronized (myDataLock) {
      return myWorker.getCopyByName(name);
    }
  }

  public LocalChangeList addChangeList(@NotNull String name, final String comment) {
    synchronized (myDataLock) {
      final LocalChangeList changeList = myModifier.addChangeList(name, comment);
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
      return changeList;
    }
  }

  public void removeChangeList(LocalChangeList list) {
    synchronized (myDataLock) {
      myModifier.removeChangeList(list.getName());
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
    }
  }

  /**
   * does no modification to change lists, only notification is sent
   */
  @NotNull
  public Runnable prepareForChangeDeletion(final Collection<Change> changes) {
    final Map<String, LocalChangeList> lists = new HashMap<String, LocalChangeList>();
    final Map<String, List<Change>> map = new HashMap<String, List<Change>>();
    synchronized (myDataLock) {
      for (Change change : changes) {
        LocalChangeList changeList = getChangeList(change);
        if (changeList == null) {
          changeList = getDefaultChangeList();
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
        synchronized (myDataLock) {
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
    synchronized (myDataLock) {
      myModifier.setDefault(list.getName());
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
    }
  }

  @Nullable
  public LocalChangeList getDefaultChangeList() {
    synchronized (myDataLock) {
      return myWorker.getDefaultListCopy();
    }
  }

  @Nullable
  public LocalChangeList getChangeList(Change change) {
    synchronized (myDataLock) {
      return myWorker.listForChange(change);
    }
  }

  /**
   * @deprecated
   * better use normal comparison, with equals
   */
  @Nullable
  public LocalChangeList getIdentityChangeList(Change change) {
    synchronized (myDataLock) {
      final List<LocalChangeList> lists = myWorker.getListsCopy();
      for (LocalChangeList list : lists) {
        for(Change oldChange: list.getChanges()) {
          if (oldChange == change) {
            return list;
          }
        }
      }
      return null;
    }
  }

  @Override
  public boolean isInUpdate() {
    synchronized (myDataLock) {
      return myModifier.isInsideUpdate();
    }
  }

  @Nullable
  public Change getChange(VirtualFile file) {
    synchronized (myDataLock) {
      final String name = myWorker.getListName(file);
      if (name != null) {
        final LocalChangeList list = myWorker.getCopyByName(name);
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
    synchronized (myDataLock) {
      final String name = myWorker.getListName(file.getVirtualFile());
      if (name != null) {
        final LocalChangeList list = myWorker.getCopyByName(name);
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
    return myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file);
  }

  @NotNull
  public FileStatus getStatus(VirtualFile file) {
    synchronized (myDataLock) {
      if (myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file)) return FileStatus.UNKNOWN;
      if (myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).containsFile(file)) return FileStatus.HIJACKED;
      if (myComposite.getVFHolder(FileHolder.HolderType.IGNORED).containsFile(file)) return FileStatus.IGNORED;

      final FileStatus status = myWorker.getStatus(file);
      if (status != null) {
        return status;
      }
      if (myComposite.getSwitchedFileHolder().containsFile(file)) return FileStatus.SWITCHED;
      return FileStatus.NOT_CHANGED;
    }
  }

  @NotNull
  public Collection<Change> getChangesIn(VirtualFile dir) {
    return getChangesIn(new FilePathImpl(dir));
  }

  @NotNull
  public Collection<Change> getChangesIn(final FilePath dirPath) {
    synchronized (myDataLock) {
      return myWorker.getChangesIn(dirPath);
    }
  }

  public void moveChangesTo(LocalChangeList list, final Change[] changes) {
    synchronized (myDataLock) {
      myModifier.moveChangesTo(list.getName(), changes);
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
          synchronized (myDataLock) {
            List<Change> changesToMove = new ArrayList<Change>();
            final LocalChangeList defaultList = getDefaultChangeList();
            for(Change change: defaultList.getChanges()) {
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
      }, false, false, VcsBundle.message("change.lists.manager.add.unversioned"), false);
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
    synchronized (myDataLock) {
      myIgnoredIdeaLevel.clear();
      new ChangeListManagerSerialization(myIgnoredIdeaLevel, myWorker).readExternal(element);
      if ((! myWorker.isEmpty()) && getDefaultChangeList() == null) {
        setDefaultChangeList(myWorker.getListsCopy().get(0));
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    synchronized (myDataLock) {
      new ChangeListManagerSerialization(myIgnoredIdeaLevel, myWorker).writeExternal(element);
    }
  }

  // used in TeamCity
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
    myIgnoredIdeaLevel.add(filesToIgnore);
    updateIgnoredFiles(true);
  }

  public void setFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
    myIgnoredIdeaLevel.set(filesToIgnore);
    updateIgnoredFiles(true);
  }

  private void updateIgnoredFiles(final boolean checkIgnored) {
    synchronized (myDataLock) {
      List<VirtualFile> unversionedFiles = myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles();
      List<VirtualFile> ignoredFiles = myComposite.getVFHolder(FileHolder.HolderType.IGNORED).getFiles();
      boolean somethingChanged = false;
      for(VirtualFile file: unversionedFiles) {
        if (isIgnoredFile(file)) {
          somethingChanged = true;
          myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).removeFile(file);
          myComposite.getVFHolder(FileHolder.HolderType.IGNORED).addFile(file);
        }
      }
      if (checkIgnored) {
        for(VirtualFile file: ignoredFiles) {
          if (!isIgnoredFile(file)) {
            somethingChanged = true;
            // the file may have been reported as ignored by the VCS, so we can't directly move it to unversioned files
            VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
          }
        }
      }
      if (somethingChanged) {
        FileStatusManager.getInstance(getProject()).fileStatusesChanged();
        ChangesViewManager.getInstance(myProject).scheduleRefresh();
      }
    }
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    return myIgnoredIdeaLevel.getFilesToIgnore();
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    return myIgnoredIdeaLevel.isIgnoredFile(file);
  }

  @Nullable
  public String getSwitchedBranch(final VirtualFile file) {
    return myComposite.getSwitchedFileHolder().getBranchForFile(file);    
  }

  @Override
  public String getDefaultListName() {
    synchronized (myDataLock) {
      return myWorker.getDefaultListName();
    }
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

  public boolean setReadOnly(final String name, final boolean value) {
    // there could be external read action taken under which another method of ChangeListManager guarged by myDataLock
    // is being called; so to avoid deadlock use the same sequence of locks - read action first (internal in getInstanceChecked)
    final ChangesViewManager changesView;
    try {
      changesView = ChangesViewManager.getInstanceChecked(myProject);
    }
    catch (ProcessCanceledException e) {
      return false;
    }

    synchronized (myDataLock) {
      final boolean result = myModifier.setReadOnly(name, value);
      changesView.scheduleRefresh();
      return result;
    }
  }

  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    synchronized (myDataLock) {
      final boolean result = myModifier.editName(fromName, toName);
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
      return result;
    }
  }

  public String editComment(@NotNull final String fromName, final String newComment) {
    synchronized (myDataLock) {
      final String oldComment = myModifier.editComment(fromName, newComment);
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
      return oldComment;
    }
  }

  /**
   * Can be called only from not AWT thread; to do smthg after ChangeListManager refresh, call invokeAfterUpdate
   */
  public boolean ensureUpToDate(final boolean canBeCanceled) {
    final EnsureUpToDateFromNonAWTThread worker = new EnsureUpToDateFromNonAWTThread(myProject);
    worker.execute();
    return worker.isDone();
  }
}
