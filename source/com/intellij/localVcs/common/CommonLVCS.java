package com.intellij.localVcs.common;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.localVcs.impl.OldLvcsImplemetation;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.localvcs.integration.LocalHistoryAction;
import com.intellij.localvcs.integration.LocalHistoryBundle;
import com.intellij.localvcs.integration.LocalHistoryConfiguration;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.localVcs.*;
import com.intellij.openapi.localVcs.impl.LvcsActionImpl;
import com.intellij.openapi.localVcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

public class CommonLVCS extends LocalVcs implements ProjectComponent, FileContentProvider, ModuleRootListener {
  private OldLvcsImplemetation myImplementation;
  private LocalVcsUserActivitiesRegistry myUserActivitiesRegistry = new LocalVcsUserActivitiesRegistry();
  private LvcsFileTracker myTracker;
  private VirtualFile[] myRoots = null;
  private FileIndex myFileIndex;
  private FileTypeManager myFileTypeManager;
  private LvcsAction myAction = null;
  private final Project myProject;
  private LocalHistoryConfiguration myConfiguration;

  private static final int DO_NOT_PERFORM_PURGING = -1;

  @NonNls protected static final String VCS_DIR = "vcs";
  @NonNls protected static final String ACTIVITIES_FILE_NAME = "activities.dat";
  @NonNls protected static final String TRANSACTION_LOC_FILE_NAME = "transaction.lock";

  private static final int REASONABLE_ACTION_NAME_LENGTH = 1024;

  private final LocalVcsPurgingProviderImpl myLocalVcsPurgingProvider = new LocalVcsPurgingProviderImpl();

  private File myVcsLocation;
  public static final String EXTERNAL_CHANGES_ACTION = LocalHistoryBundle.message("local.vcs.external.changes.action.name");
  private static final Logger LOG = Logger.getInstance("#com.intellij.localVcs.common.CommonLVCS");

  private boolean myIsLocked = false;
  private boolean myVcsWasRebuilt = false;
  private boolean myInitialized = false;
  private boolean myIsActive = false;
  private MessageBusConnection myConnection;

  public CommonLVCS(final Project project,
                    final ProjectRootManagerEx projectRootManager,
                    final FileTypeManager fileTypeManager,
                    final StartupManager startupManager,
                    final LocalHistoryConfiguration configuration) {
    myProject = project;
    myVcsLocation = findProjectVcsLocation();
    if (!isOldLvcsEnabled()) return;

    myFileIndex = projectRootManager.getFileIndex();
    myFileTypeManager = fileTypeManager;
    myConfiguration = configuration;

    myImplementation = new OldLvcsImplemetation(project, this);

    ((StartupManagerEx)startupManager).registerPreStartupActivity(new Runnable() {
      public void run() {
        runStartupActivity();
      }
    });

    myRefreshRootsOperation = new DelayedSyncOperation(myProject, this, LocalHistoryBundle.message("operation.name.refreshing.roots"));
    startupManager.getFileSystemSynchronizer().registerCacheUpdater(myRefreshRootsOperation);
    projectRootManager.registerChangeUpdater(myRefreshRootsOperation);

    myTracker = new LvcsFileTracker(this);
    registerAll();
  }

  public LocalHistoryConfiguration getConfiguration() {
    checkOldLvcsEnabled();
    return myConfiguration;
  }

  public synchronized void commitFile(final LvcsFileRevision lvcsFileRevision, final byte[] bytes) {
    checkOldLvcsEnabled();
    myImplementation.commitFile(lvcsFileRevision, bytes);
  }

  public synchronized void addLvcsLabelListener(final LvcsLabelListener listener) {
    checkOldLvcsEnabled();
    myImplementation.addLvcsLabelListener(listener);
  }

  public synchronized void removeLvcsLabelListener(final LvcsLabelListener listener) {
    checkOldLvcsEnabled();
    myImplementation.removeLvcsLabelListener(listener);
  }

  public synchronized LocalVcsPurgingProvider getLocalVcsPurgingProvider() {
    //if (isOldLvcsEnabled()) return myLocalVcsPurgingProvider;
    return new LocalVcsPurgingProvider() {
      public void registerLocker(LocalVcsItemsLocker locker) {
      }

      public void unregisterLocker(LocalVcsItemsLocker locker) {
      }

      public boolean itemCanBePurged(LvcsRevision lvcsRevisionFor) {
        return true;
      }
    };
  }

  public synchronized UpToDateLineNumberProvider getUpToDateLineNumberProvider(final Document document, final String upToDateContent) {
    return new UpToDateLineNumberProviderImpl(document, myProject, upToDateContent);
  }

  public synchronized boolean rollbackToLabel(final LvcsLabel label,
                                              final boolean requestConfirmation,
                                              final String confirmationMessage,
                                              final String confirmationTitle) {
    checkOldLvcsEnabled();
    return myImplementation.rollbackToLabel(label, requestConfirmation, confirmationMessage, confirmationTitle);
  }

  public synchronized boolean rollbackToLabel(final LvcsLabel label, final boolean requestConfirmation) {
    checkOldLvcsEnabled();
    return myImplementation.rollbackToLabel(label, requestConfirmation);
  }

  public synchronized LvcsLabel[] getAllLabels() {
    checkOldLvcsEnabled();
    return myImplementation.getAllLabels();
  }

  public synchronized LvcsRevision[] getRevisions(final String path, final LvcsLabel label) {
    checkOldLvcsEnabled();
    return myImplementation.getChanges(path, label);
  }

  public synchronized LvcsRevision[] getRevisions(final LvcsLabel label1, final LvcsLabel label2) {
    checkOldLvcsEnabled();
    return myImplementation.getChanges(label1, label2);
  }

  @Nullable
  public synchronized LvcsDirectory findDirectory(final String dirPath, final boolean ignoreDeleted) {
    checkOldLvcsEnabled();
    return myImplementation.findDirectory(dirPath, ignoreDeleted);
  }

  @Nullable
  public synchronized LvcsFile findFile(final String filePath, final boolean ignoreDeleted) {
    checkOldLvcsEnabled();
    return myImplementation.findFile(filePath, ignoreDeleted);
  }

  @Nullable
  public synchronized LvcsFileRevision findFileRevisionByDate(final String filePath, long date) {
    checkOldLvcsEnabled();
    return myImplementation.findFileRevisionByDate(filePath, date);
  }

  @Nullable
  public synchronized LvcsFileRevision findFileRevision(final String filePath, final LvcsLabel label) {
    checkOldLvcsEnabled();
    return myImplementation.findFileRevision(filePath, label);
  }

  @Nullable
  public synchronized LvcsFileRevision findFileRevision(final String filePath, final boolean ignoreDeleted) {
    checkOldLvcsEnabled();
    return myImplementation.findFileRevision(filePath, ignoreDeleted);
  }

  public void beforeRootsChange(ModuleRootEvent event) {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "LocalVcs";
  }

  public synchronized void resynchronizeRoots() {
    if (!myInitialized) return;
    refreshRoots();
    runSynchronizationUsing(new ImmediateSyncOperation(myProject, this));
  }

  private synchronized void refreshRoots() {
    myRoots = calculateRoots();
  }

  private synchronized void runSynchronizationUsing(StructureSyncOperation realOperation) {
    new LvcsRootSynchronizer(this, myTracker, realOperation).syncProjectRoots();
  }

  public long getPurgingPeriod() {
    if (!myConfiguration.LOCAL_VCS_ENABLED) return 0;
    return myConfiguration.PURGING_PERIOD;
  }

  public synchronized LvcsLabel addLabel(final String name, final String path) {
    if (isNewLvcsEnabled()) {
      putNewLocalHistoryLabel(name, path);
      return null;
    }

    return myImplementation.addLabel(name, path);
  }

  public synchronized LvcsLabel addLabel(final byte type, final String name, final String path) {
    if (isNewLvcsEnabled()) {
      putNewLocalHistoryLabel(name, path);
      return null;
    }
    return myImplementation.addLabel(type, name, path);
  }

  private void putNewLocalHistoryLabel(final String name, final String path) {
    if (path.equals("")) {
      LocalHistory.putLabel(myProject, name);
    }
    else {
      LocalHistory.putLabel(myProject, path, name);
    }
  }

  public synchronized void markModuleSourcesAsCurrent(final Module module, final String label) {
    checkOldLvcsEnabled();
    myImplementation.markModuleSourcesAsCurrent(module, label);
  }

  public synchronized void markSourcesAsCurrent(final String label) {
    checkOldLvcsEnabled();
    myImplementation.markSourcesAsCurrent(label);
  }

  public boolean isEnabled() {
    return myConfiguration.LOCAL_VCS_ENABLED;
  }

  private synchronized VirtualFile[] calculateRoots() {
    return getProjectRoots();
  }

  private synchronized VirtualFile[] getProjectRoots() {
    return ProjectRootManager.getInstance(myProject).getContentRoots();
  }

  @Nullable
  public synchronized LvcsDirectory findDirectory(final String path) {
    checkOldLvcsEnabled();

    if (!myIsLocked) return null;
    return myImplementation.findDirectory(path);
  }

  public synchronized LvcsDirectory addDirectory(final String path, final VirtualFile onDisk) {
    LOG.assertTrue(myIsLocked);
    return myImplementation.addDirectory(path, onDisk);
  }

  @Nullable
  public synchronized LvcsFile findFile(final String path) {
    checkOldLvcsEnabled();

    if (!myIsLocked) return null;
    return myImplementation.findFile(path);
  }

  public synchronized String[] getRootPaths() {
    checkOldLvcsEnabled();
    return myImplementation.getRootPaths();
  }


  public synchronized boolean isUnderVcs(VirtualFile file) {
    checkOldLvcsEnabled();

    if (!file.isInLocalFileSystem()) {
      return false;
    }
    if (!myFileIndex.isInContent(file)) return false;

    if (file.isDirectory()) {
      return true;
    }
    else {
      final FileType type = myFileTypeManager.getFileTypeByFile(file);
      return isValidFileType(type);
    }
  }

  public synchronized Project getProject() {
    return myProject;
  }

  public synchronized int purge() {
    if (!isOldLvcsEnabled()) return 0;

    final LocalHistoryConfiguration configuration = LocalHistoryConfiguration.getInstance();
    if (configuration.PURGING_PERIOD == DO_NOT_PERFORM_PURGING) return 0;
    long purgingPeriod = configuration.PURGING_PERIOD;
    if (!isEnabled()) purgingPeriod = 0;
    long timeToPurgeBefore = myUserActivitiesRegistry.getActivityPeriodStart(System.currentTimeMillis(), purgingPeriod);
    return myImplementation.purge(timeToPurgeBefore);
  }

  private synchronized void shutdown() {
    checkOldLvcsEnabled();

    if (myAction != null) {
      myAction.finish();
    }
    LOG.info("enter: shutdown()");
    save();
    close();
    myImplementation.clearLabels();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      clear();
    }
  }

  public synchronized void close() {
    checkOldLvcsEnabled();

    myIsLocked = false;
    try {
      myImplementation.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    LOG.info("Closed local history at " + myVcsLocation.getAbsolutePath());
  }

  public synchronized VirtualFileListener getVirtualFileListener() {
    checkOldLvcsEnabled();
    return myTracker;
  }

  private boolean myCanProvideContents = false;
  private DelayedSyncOperation myRefreshRootsOperation;

  public synchronized VirtualFile[] getRoots() {
    checkOldLvcsEnabled();
    if (myRoots == null) refreshRoots();
    return myRoots;
  }

  public synchronized VirtualFile[] getCoveredDirectories() {
    checkOldLvcsEnabled();

    //TODO: filter out roots from JarFilesystem.
    if (isOldLvcsEnabled()) {
      return getRoots();
    }
    else {
      return new VirtualFile[0];
    }
  }

  @Nullable
  public synchronized ProvidedContent getProvidedContent(VirtualFile file) {
    checkOldLvcsEnabled();

    String path = file.getPath();
    LOG.assertTrue(myCanProvideContents, path);
    LOG.assertTrue(file.isValid());
    contentRequestedFor(file);
    LvcsFile lvcsFile = findFile(path);
    if (lvcsFile != null && lvcsFile.getTimeStamp() != file.getTimeStamp()) {
      LOG.error(
        "lvcsFile.getTimeStamp() = " + lvcsFile.getTimeStamp() + ", " + "file.getTimeStamp()=" + file.getTimeStamp() + ", file=" + file);
    }
    if ((lvcsFile == null) && (fileOrParentIsDeleted(file))) {
      return null;
    }
    if (isUnderVcs(file) && lvcsFile == null) {
      LOG.assertTrue(false, file.getPath());
    }
    return ProvidedContentOnLvcsFile.createOn(lvcsFile, file);
  }

  private synchronized void contentRequestedFor(VirtualFile file) {
    myRefreshRootsOperation.contentRequestedFor(file);
    myTracker.contentRequestedFor(file);
  }

  private void checkLocalVCSWasSavedCorrectly() throws CouldNotLoadLvcsException {
    final File transactionFile = getTransactionFile();
    if (transactionFile.exists()) {
      throw new CouldNotLoadLvcsException(LocalHistoryBundle.message("exception.text.local.history.was.incorrectly.saved"));
    }
  }

  public synchronized File getTransactionFile() {
    checkOldLvcsEnabled();
    return new File(myVcsLocation, TRANSACTION_LOC_FILE_NAME);
  }

  public LocalHistoryAction startExternalChangesAction() {
    checkOldLvcsEnabled();
    return startAction_New(EXTERNAL_CHANGES_ACTION, "", true);
  }

  public synchronized LocalHistoryAction startAction_New(String action, String path, boolean isExternalChanges) {
    if (!isOldLvcsEnabled()) {
      return LocalHistory.startAction(myProject, action);
    }

    if (action == null) return LvcsAction.EMPTY;
    if (action.length() > REASONABLE_ACTION_NAME_LENGTH) {
      action = action.substring(0, REASONABLE_ACTION_NAME_LENGTH);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: started(action='" + action + "')");
    }
    if (myAction != null) return LvcsAction.EMPTY;
    commitAllUnsavedDocuments();
    myAction = myImplementation.startAction(action, path);
    return myAction;
  }

  public synchronized LvcsAction startAction(String action, String path, boolean isExternalChanges) {
    if (!isOldLvcsEnabled()) {
      return new LvcsActionImpl(this, action, -1, null);
    }

    if (action == null) return LvcsAction.EMPTY;
    if (action.length() > REASONABLE_ACTION_NAME_LENGTH) {
      action = action.substring(0, REASONABLE_ACTION_NAME_LENGTH);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: started(action='" + action + "')");
    }
    if (myAction != null) return LvcsAction.EMPTY;
    commitAllUnsavedDocuments();
    myAction = myImplementation.startAction(action, path);
    return myAction;
  }

  public synchronized void endAction(LvcsActionImpl action) {
    if (!isOldLvcsEnabled()) return;

    commitAllUnsavedDocuments();
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: finished()");
    }
    LOG.assertTrue(myAction != null, action.getName());
    myImplementation.addLabelImpl(LvcsLabel.TYPE_AFTER_ACTION, "", action.getPath(), action.getName());
    myAction = null;
    saveInternal(EXTERNAL_CHANGES_ACTION.equals(action.getName()));
  }

  private void commitAllUnsavedDocuments() {
    Document[] unsavedDocuments = FileDocumentManager.getInstance().getUnsavedDocuments();
    for (Document unsavedDocument : unsavedDocuments) {
      commitUnsavedDocument(unsavedDocument);
    }
  }

  private synchronized void commitUnsavedDocument(Document unsavedDocument) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(unsavedDocument);
    if (file == null || !file.isValid() || file instanceof LightVirtualFile) return;
    if (isUnderVcs(file)) {
      LvcsFile lvcsFile = findFile(file.getPath(), true);
      if (lvcsFile == null) return;
      if (isOutsideOfProjectRoot(lvcsFile.getRevision())) return;
      try {
        Charset charset = file.getCharset();
        LOG.assertTrue(charset != null);
        myImplementation.commitFile(lvcsFile.getRevision(), unsavedDocument.getText().getBytes(charset.name()));
      }
      catch (UnsupportedEncodingException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  private static ProgressIndicator getProgress() {
    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      return ProgressManager.getInstance().getProgressIndicator();
    }
    return null;
  }

  public synchronized void save() {
    if (!isOldLvcsEnabled()) return;

    saveInternal(true);
  }

  private synchronized void saveInternal(final boolean purge) {
    if (!myIsLocked) return;
    if (purge) {
      purge();
    }
    try {
      createTransactionFile();

      myImplementation.save(getVcsLocation());

      DataOutputStream activitiesOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getActivitiesRegistryFile())));
      try {
        myUserActivitiesRegistry.save(activitiesOut);
      }
      finally {
        activitiesOut.close();
      }
      FileUtil.delete(getTransactionFile());
    }
    catch (final Throwable e) {
      LOG.info(e);
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(
              LocalHistoryBundle.message("message.text.could.not.save.local.history.with.error", e.getLocalizedMessage()),
              LocalHistoryBundle.message("message.title.saving.local.history"), Messages.getErrorIcon());
          }
        });
      }
    }
  }

  private void createTransactionFile() throws IOException {
    File f = getTransactionFile();
    f.getParentFile().mkdirs();
    f.createNewFile();
  }

  private synchronized void load() throws CouldNotLoadLvcsException, IOException {
    LOG.info("enter: load()");
    checkLocalVCSWasSavedCorrectly();
    myImplementation.load(myVcsLocation);
    File activitiesRegistryFile = getActivitiesRegistryFile();
    if (activitiesRegistryFile.isFile()) {
      DataInputStream activitiesInput = new DataInputStream(new BufferedInputStream(new FileInputStream(activitiesRegistryFile)));
      try {
        myUserActivitiesRegistry.load(activitiesInput);
      }
      finally {
        activitiesInput.close();
      }
    }
  }

  private synchronized void clearAndRecreateLocation() {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager == null ? null : progressManager.getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(LocalHistoryBundle.message("progress.text.clearing.local.history"));
    }
    myImplementation.clearAndRecreate(myVcsLocation);
    if (progress != null) {
      progress.popState();
    }
    myVcsWasRebuilt = true;
  }

  private synchronized File getActivitiesRegistryFile() {
    return new File(myVcsLocation, ACTIVITIES_FILE_NAME);
  }

  public synchronized void setCanProvideContents(boolean canProvide) {
    myCanProvideContents = canProvide;
  }

  private synchronized boolean fileOrParentIsDeleted(VirtualFile file) {
    if (myTracker == null) return false;
    VirtualFile current = file;
    while (current != null) {
      if (myTracker.fileIsDeleted(current)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  public boolean isOutsideOfProjectRoot(LvcsFileRevision file) {
    checkOldLvcsEnabled();

    VirtualFile[] projectRoots = getProjectRoots();
    for (VirtualFile root : projectRoots) {
      VirtualFile virtualFile = root.getFileSystem().findFileByPath(file.getAbsolutePath());
      if (virtualFile == null) continue;
      if (VfsUtil.isAncestor(root, virtualFile, false)) return false;
    }
    return true;
  }

  public synchronized void onUserAction() {
    checkOldLvcsEnabled();

    myUserActivitiesRegistry.registerActivity(System.currentTimeMillis());
  }

  private synchronized void registerAll() {
    getVirtualFileManager().registerFileContentProvider(this);

    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent event) {
        resynchronizeRoots();
      }
    });
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, this);

    getVirtualFileManager().registerRefreshUpdater(myTracker.getRefreshUpdater());
    myIsActive = true;
  }

  public synchronized void unregisterAll() {
    checkOldLvcsEnabled();

    myIsActive = false;
    myInitialized = false;
    getVirtualFileManager().unregisterFileContentProvider(this);
    myConnection.disconnect();

    if (myTracker != null) {
      myTracker.dispose();
    }
    getVirtualFileManager().unregisterRefreshUpdater(myTracker.getRefreshUpdater());
  }

  private synchronized void synchronizeRoots() {
    checkOldLvcsEnabled();

    /*myRefreshRootsOperation = new DelayedSyncOperation(myProject, this, LocalHistoryBundle.message("operation.name.refresh.files.on.startup")) {
      public void updatingDone() {
        synchronized (CommonLVCS.this) {
          super.updatingDone();
          CommonLVCS.this.myRefreshRootsOperation = null;
        }
      }

      public void canceled() {
        LOG.assertTrue(false);
      }
    };

    myRefreshRootsOperation.setLvcsAction(startExternalChangesAction());*/
    runSynchronizationUsing(myRefreshRootsOperation);
  }

  public static boolean isAction(LvcsLabel label1, LvcsLabel label2) {
    return label1.getType() == LvcsLabel.TYPE_BEFORE_ACTION && label2.getType() == LvcsLabel.TYPE_AFTER_ACTION &&
           label1.getVersionId() == label2.getVersionId();
  }

  private static VirtualFileManagerEx getVirtualFileManager() {
    return (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
  }

  private synchronized void runStartupActivity() {
    refreshRoots();
    initialize(false);
  }

  public synchronized void initialize(boolean sync) {
    checkOldLvcsEnabled();

    if (!myIsActive) registerAll();
    if (myIsLocked) return;
    _init();
    if (!myIsLocked) return;
    if (!myVcsWasRebuilt && LocalHistoryConfiguration.getInstance().ADD_LABEL_ON_PROJECT_OPEN) {
      myImplementation.addLabel(LocalHistoryBundle.message("local.vcs.label.name.project.opened"), "");
    }

    if (sync) {
      resynchronizeRoots();
      setCanProvideContents(true);
    }
    else {
      synchronizeRoots();
    }
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      fileStatusManager.fileStatusesChanged();
    }
  }

  private synchronized void _init() {
    myInitialized = true;
    myVcsLocation.mkdirs();
    myVcsLocation.mkdir();
    ProgressIndicator progress = getProgress();
    if (progress != null) {
      progress.pushState();
      progress.setText(LocalHistoryBundle.message("progress.text.initializing.local.history"));
    }
    try {
      myImplementation._init(myVcsLocation);
      load();
    }
    catch (Throwable t) {
      LOG.info(t);
      clearAndRecreateLocation();
    }
    if (progress != null) {
      progress.popState();
    }
    myIsLocked = true;
    myImplementation.markSourcesAsCurrentInInitialization();
  }

  public synchronized void rootsChanged(ModuleRootEvent event) {
    checkOldLvcsEnabled();

    if (!myInitialized) return;
    refreshRoots();
    if (myTracker.isRefreshInProgress()) return;
    if (myRefreshRootsOperation.isUpdating()) {
      LOG.assertTrue(!myProject.isDisposed());
      LOG.assertTrue(false, myRefreshRootsOperation.toString() + " --- " + System.identityHashCode(myProject));
    }

    myRefreshRootsOperation.setLvcsAction(startExternalChangesAction());
    runSynchronizationUsing(myRefreshRootsOperation);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    if (!isOldLvcsEnabled()) return;
    unregisterAll();
    ProjectRootManagerEx.getInstanceEx(myProject).unregisterChangeUpdater(myRefreshRootsOperation);
    shutdown();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public synchronized boolean isAvailable() {
    checkOldLvcsEnabled();
    //if (!isOldLvcsEnabled()) return false;
    return myIsLocked;
  }

  public synchronized LvcsAction getAction() {
    checkOldLvcsEnabled();
    return myAction;
  }

  public void reportLvcsHistoryIsCorrupted(final String message) {
    checkOldLvcsEnabled();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("Local history is corrupt and will be rebuilt: \n" + message);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showMessageDialog(LocalHistoryBundle.message("message.text.local.history.corrupt.with.message", message),
                                   LocalHistoryBundle.message("message.title.local.history.corrupt"), Messages.getWarningIcon());
      }
    }, ModalityState.NON_MODAL);
    Runnable rebuildAction = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            clearAndRecreateLocation();
            resynchronizeRoots();
          }
        }, LocalHistoryBundle.message("progress.title.rebuilding.local.history"), false, myProject);
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      rebuildAction.run();
    }
    else {
      try {
        SwingUtilities.invokeAndWait(rebuildAction);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
    }
  }

  public synchronized void clear() {
    if (!isOldLvcsEnabled()) return;

    myImplementation.clear(myVcsLocation);
    FileUtil.delete(myVcsLocation);
  }

  public synchronized File getVcsLocation() {
    return myVcsLocation;
  }

  public synchronized OldLvcsImplemetation getImplementation() {
    checkOldLvcsEnabled();
    return myImplementation;
  }

  public synchronized LvcsFileRevision addFile(final LvcsDirectoryRevision dir1, final VirtualFileInfo virtualFileInfoOnVirtualFile) {
    checkOldLvcsEnabled();
    return myImplementation.addFile(dir1, virtualFileInfoOnVirtualFile);
  }

  public synchronized LvcsFileRevision commitFile(final LvcsFileRevision file, final VirtualFileInfo virtualFileInfo) {
    checkOldLvcsEnabled();
    return myImplementation.commitFile(file, virtualFileInfo);
  }

  public synchronized void checkConsistency(final boolean shouldntContainScheduledForRemoval) {
    checkOldLvcsEnabled();
    myImplementation.checkConsistency(shouldntContainScheduledForRemoval);
  }

  private static boolean isValidFileType(final FileType type) {
    return type != StdFileTypes.IDEA_WORKSPACE && type != StdFileTypes.IDEA_PROJECT && type != StdFileTypes.IDEA_MODULE && !type.isBinary();
  }

  private File findProjectVcsLocation() {
    return new File(new File(PathManager.getSystemPath(), VCS_DIR), myProject.getLocationHash());
  }

  public boolean isInitialized() {
    checkOldLvcsEnabled();
    return myInitialized;
  }

  private boolean isOldLvcsEnabled() {
    return !LocalHistory.isEnabled(myProject);
  }

  private boolean isNewLvcsEnabled() {
    return LocalHistory.isEnabled(myProject);
  }

  private void checkOldLvcsEnabled() {
    if (!isOldLvcsEnabled()) throw new RuntimeException("old lvcs is disabled");
  }
}
