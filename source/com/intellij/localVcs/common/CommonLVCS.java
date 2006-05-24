package com.intellij.localVcs.common;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.localVcs.impl.OldLvcsImplemetation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.localVcs.*;
import com.intellij.openapi.localVcs.impl.LvcsActionImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

public class CommonLVCS extends LocalVcs implements ProjectComponent, FileContentProvider, ModuleRootListener, SettingsSavingComponent {
  private OldLvcsImplemetation myImplementation;
  private LocalVcsUserActivitiesRegistry myUserActivitiesRegistry = new LocalVcsUserActivitiesRegistry();
  private LvcsFileTracker myTracker;
  private VirtualFile[] myRoots = null;
  private FileIndex myFileIndex;
  private FileTypeManager myFileTypeManager;
  private LvcsAction myAction = null;
  private final Project myProject;
  private final LvcsConfiguration myConfiguration;

  private static final int DO_NOT_PERFORM_PURGING = -1;

  @NonNls protected static final String VCS_DIR = "vcs";
  @NonNls protected static final String ACTIVITIES_FILE_NAME = "activities.dat";
  @NonNls protected static final String TRANSACTION_LOC_FILE_NAME = "transaction.lock";

  private static final int REASONABLE_ACTION_NAME_LENGTH = 1024;

  private final LocalVcsPurgingProviderImpl myLocalVcsPurgingProvider = new LocalVcsPurgingProviderImpl();

  private File myVcsLocation;
  public static final String EXTERNAL_CHANGES_ACTION = LocalVcsBundle.message("local.vcs.external.changes.action.name");
  private static final Logger LOG = Logger.getInstance("#com.intellij.localVcs.common.CommonLVCS");

  private boolean myIsLocked = false;
  private boolean myVcsWasRebuilt = false;

  public CommonLVCS(final Project project,
                    final ProjectRootManager projectRootManager,
                    final FileTypeManager fileTypeManager,
                    final StartupManagerImpl startupManagerEx,
                    final LvcsConfiguration configuration) {

    myProject = project;
    myFileIndex = projectRootManager.getFileIndex();
    myFileTypeManager = fileTypeManager;
    myConfiguration = configuration;
    myVcsLocation = findProjectVcsLocation(myProject.getProjectFile());
    myImplementation = new OldLvcsImplemetation(project, this);

    startupManagerEx.registerPreStartupActivity(new Runnable() {
      public void run() {
        runStartupActivity();
      }
    });
  }

  public LvcsConfiguration getConfiguration() {
    return myConfiguration;
  }

  public synchronized void commitFile(final LvcsFileRevision lvcsFileRevision, final byte[] bytes) {
    myImplementation.commitFile(lvcsFileRevision, bytes);
  }

  public synchronized void addLvcsLabelListener(final LvcsLabelListener listener) {
    myImplementation.addLvcsLabelListener(listener);
  }

  public synchronized void removeLvcsLabelListener(final LvcsLabelListener listener) {
    myImplementation.removeLvcsLabelListener(listener);
  }

  public synchronized LocalVcsPurgingProvider getLocalVcsPurgingProvider() {
    return myLocalVcsPurgingProvider;
  }

  public synchronized UpToDateLineNumberProvider getUpToDateLineNumberProvider(final Document document, final String upToDateContent) {
    return myImplementation.getUpToDateLineNumberProvider(document, upToDateContent);
  }

  public synchronized boolean rollbackToLabel(final LvcsLabel label,
                                              final boolean requestConfirmation,
                                              final String confirmationMessage,
                                              final String confirmationTitle) {
    return myImplementation.rollbackToLabel(label, requestConfirmation, confirmationMessage, confirmationTitle);
  }

  public synchronized boolean rollbackToLabel(final LvcsLabel label, final boolean requestConfirmation) {
    return myImplementation.rollbackToLabel(label, requestConfirmation);
  }

  public synchronized LvcsLabel[] getAllLabels() {
    return myImplementation.getAllLabels();
  }

  public synchronized LvcsRevision[] getChanges(final String path, final LvcsLabel label) {
    return myImplementation.getChanges(path, label);
  }

  public synchronized LvcsRevision[] getChanges(final LvcsLabel label1, final LvcsLabel label2) {
    return myImplementation.getChanges(label1, label2);
  }

  public synchronized LvcsDirectory findDirectory(final String dirPath, final boolean ignoreDeleted) {
    return myImplementation.findDirectory(dirPath, ignoreDeleted);
  }

  public synchronized LvcsDirectory findDirectory(final String dirPath, final LvcsLabel label) {
    return myImplementation.findDirectory(dirPath, label);
  }

  public synchronized LvcsFile findFile(final String filePath, final LvcsLabel label) {
    return myImplementation.findFile(filePath, label);
  }

  public synchronized LvcsFile findFile(final String filePath, final boolean ignoreDeleted) {
    return myImplementation.findFile(filePath, ignoreDeleted);
  }

  public synchronized LvcsFileRevision findFileRevisionByDate(final String filePath, long date) {
    return myImplementation.findFileRevisionByDate(filePath, date);
  }

  public synchronized LvcsFileRevision findFileRevision(final String filePath, final LvcsLabel label) {
    return myImplementation.findFileRevision(filePath, label);
  }

  public synchronized LvcsFileRevision findFileRevision(final String filePath, final boolean ignoreDeleted) {
    return myImplementation.findFileRevision(filePath, ignoreDeleted);
  }

  public void beforeRootsChange(ModuleRootEvent event) {
  }

  @NonNls
  public String getComponentName() {
    return "LocalVcs";
  }

  private final FileTypeListener myFileTypeListener = new FileTypeListener() {
    public void beforeFileTypesChanged(FileTypeEvent event) {
    }

    public void fileTypesChanged(FileTypeEvent event) {
      resynchronizeRoots();
    }
  };

  public synchronized void resynchronizeRoots() {
    refreshRoots();
    runSynchronizationUsing(new ImmediateSyncOperation(myProject, this));
  }

  private synchronized void refreshRoots() {
    myRoots = calculateRoots();
  }

  private synchronized void runSynchronizationUsing(StructureSyncOperation realOperation) {
    runSyncOperation(realOperation);
  }

  public long getPurgingPeriod() {
    if (!myConfiguration.LOCAL_VCS_ENABLED) return 0;
    return myConfiguration.LOCAL_VCS_PURGING_PERIOD;
  }

  public synchronized LvcsLabel addLabel(final String name, final String path) {
    return myImplementation.addLabel(name, path);
  }

  public synchronized LvcsLabel addLabel(final byte type, final String name, final String path) {
    return myImplementation.addLabel(type, name, path);
  }

  public synchronized LvcsLabel addLabelImpl(final byte type, final String name, final String path, final String action) {
    return myImplementation.addLabelImpl(type, name, path, action);
  }

  public synchronized void markModuleSourcesAsCurrent(final Module module, final String label) {
    myImplementation.markModuleSourcesAsCurrent(module, label);
  }

  public synchronized void markSourcesAsCurrent(final String label) {
    myImplementation.markSourcesAsCurrent(label);
  }

  public boolean isEnabled() {
    return myConfiguration.LOCAL_VCS_ENABLED;
  }

  private synchronized void runSyncOperation(StructureSyncOperation operation) {
    LvcsRootSynchronizer sync = new LvcsRootSynchronizer(this, myTracker, operation);
    sync.syncProjectRoots();
  }

  private synchronized VirtualFile[] calculateRoots() {
    return getProjectRoots();
  }

  private synchronized VirtualFile[] getProjectRoots() {
    return ProjectRootManager.getInstance(myProject).getContentRoots();
  }

  public synchronized LvcsDirectory findDirectory(final String path) {
    if (!myIsLocked) return null;
    return myImplementation.findDirectory(path);
  }

  public synchronized LvcsDirectory addDirectory(final String path, final VirtualFile onDisk) {
    LOG.assertTrue(myIsLocked);
    return myImplementation.addDirectory(path, onDisk);
  }

  public synchronized LvcsFile findFile(final String path) {
    if (!myIsLocked) return null;
    return myImplementation.findFile(path);
  }

  public synchronized String[] getRootPaths() {
    return myImplementation.getRootPaths();
  }

  public synchronized VirtualFile[] getCoveredDirectories() {
    //TODO: filter out roots from JarFilesystem.
    if (myRoots == null) refreshRoots();
    return myRoots;
  }

  public synchronized boolean isUnderVcs(VirtualFile file) {
    if (!(file.getFileSystem()instanceof LocalFileSystem)) {
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
    final LvcsConfiguration configuration = LvcsConfiguration.getInstance();
    if (configuration.LOCAL_VCS_PURGING_PERIOD == DO_NOT_PERFORM_PURGING) return 0;
    long purgingPeriod = configuration.LOCAL_VCS_PURGING_PERIOD;
    if (!isEnabled()) purgingPeriod = 0;
    long timeToPurgeBefore = myUserActivitiesRegistry.getAbsoluteTimeForActivePeriod(System.currentTimeMillis(), purgingPeriod);
    return myImplementation.purge(timeToPurgeBefore);
  }

  public synchronized void shutdown() {
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
    return myTracker;
  }

  private boolean myCanProvideContents = false;
  private DelayedSyncOperation myRefreshRootsOperation;

  public synchronized ProvidedContent getProvidedContent(VirtualFile file) {
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
    if (myRefreshRootsOperation != null) {
      myRefreshRootsOperation.contentRequestedFor(file);
    }
    else {
      myTracker.contentRequestedFor(file);
    }
  }

  public synchronized LocalVcsUserActivitiesRegistry getLocalVcsUserActivitiesRegistry() {
    return myUserActivitiesRegistry;
  }

  private void checkLocalVCSWasSavedCorrectly() throws CouldNotLoadLvcsException {
    final File transactionFile = getTransactionFile();
    if (transactionFile.exists()) {
      throw new CouldNotLoadLvcsException(LocalVcsBundle.message("exception.text.local.history.was.incorrectly.saved"));
    }
  }

  public synchronized File getTransactionFile() {
    return new File(myVcsLocation, TRANSACTION_LOC_FILE_NAME);
  }

  public LvcsAction startExternalChangesAction() {
    return startAction(EXTERNAL_CHANGES_ACTION, "", true);
  }

  public synchronized LvcsAction startAction(String action, String path, boolean isExternalChanges) {
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

  private static ProgressIndicator getProgress() {
    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      return application.getComponent(ProgressManager.class).getProgressIndicator();
    }
    return null;
  }

  public synchronized void save() {
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
              LocalVcsBundle.message("message.text.could.not.save.local.history.with.error", e.getLocalizedMessage()),
              LocalVcsBundle.message("message.title.saving.local.history"), Messages.getErrorIcon());
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
      progress.setText(LocalVcsBundle.message("progress.text.clearing.local.history"));
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

  public synchronized LvcsFileTracker getTracker() {
    return myTracker;
  }

  public synchronized boolean fileIsDeleted(VirtualFile file) {
    return myTracker != null && myTracker.fileIsDeleted(file);
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
    VirtualFile[] projectRoots = getProjectRoots();
    for (VirtualFile root : projectRoots) {
      VirtualFile virtualFile = root.getFileSystem().findFileByPath(file.getAbsolutePath());
      if (virtualFile == null) continue;
      if (VfsUtil.isAncestor(root, virtualFile, false)) return false;
    }
    return true;
  }

  public synchronized void onUserAction() {
    myUserActivitiesRegistry.onUserAction(System.currentTimeMillis());
  }

  private synchronized void registerAll() {
    getVirtualFileManager().registerFileContentProvider(this);
    myFileTypeManager.addFileTypeListener(myFileTypeListener);
    ProjectRootManager.getInstance(myProject).addModuleRootListener(this);
    getVirtualFileManager().registerRefreshUpdater(myTracker.getRefreshUpdater());
    ProjectRootManagerEx.getInstanceEx(myProject).registerChangeUpdater(myTracker.getRefreshUpdater());
  }

  public synchronized void unregisterAll() {
    if (myTracker == null) return;
    getVirtualFileManager().unregisterFileContentProvider(this);
    myFileTypeManager.removeFileTypeListener(myFileTypeListener);
    if (myTracker != null) {
      myTracker.dispose();
    }
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(this);
    getVirtualFileManager().unregisterRefreshUpdater(myTracker.getRefreshUpdater());
    ProjectRootManagerEx.getInstanceEx(myProject).unregisterChangeUpdater(myTracker.getRefreshUpdater());
  }

  private synchronized void synchronizeRoots() {
    myRefreshRootsOperation = new DelayedSyncOperation(myProject, this, LocalVcsBundle.message("operation.name.refresh.files.on.startup")) {
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

    myRefreshRootsOperation.setLvcsAction(startExternalChangesAction());
    runSynchronizationUsing(myRefreshRootsOperation);

    StartupManagerEx startupManager = StartupManagerEx.getInstanceEx(myProject);
    FileSystemSynchronizer synchronizer = startupManager.getFileSystemSynchronizer();
    synchronizer.registerCacheUpdater(myRefreshRootsOperation);
  }

  public static boolean isAction(LvcsLabel label1, LvcsLabel label2) {
    return label1.getType() == LvcsLabel.TYPE_BEFORE_ACTION && label2.getType() == LvcsLabel.TYPE_AFTER_ACTION &&
           label1.getVersionId() == label2.getVersionId();
  }

  private static VirtualFileManagerEx getVirtualFileManager() {
    return (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
  }

  private synchronized void runStartupActivity() {
    initialize(false);
  }

  public synchronized void initialize(boolean sync) {
    if (myIsLocked) return;
    _init();
    if (!myIsLocked) return;
    if (!myVcsWasRebuilt && LvcsConfiguration.getInstance().ADD_LABEL_ON_PROJECT_OPEN) {
      myImplementation.addLabel(LocalVcsBundle.message("local.vcs.label.name.project.opened"), "");
    }
    myTracker = new LvcsFileTracker(this);
    if (sync) {
      resynchronizeRoots();
      setCanProvideContents(true);
    }
    else {
      synchronizeRoots();
    }
    registerAll();
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      fileStatusManager.fileStatusesChanged();
    }
  }

  synchronized void _init() {
    myVcsLocation.mkdirs();
    myVcsLocation.mkdir();
    ProgressIndicator progress = getProgress();
    if (progress != null) {
      progress.pushState();
      progress.setText(LocalVcsBundle.message("progress.text.initializing.local.history"));
    }
    try {
      myImplementation._init(myVcsLocation);
      load();
    }
    catch (Exception e) {
      LOG.info(e);
      clearAndRecreateLocation();
    }
    if (progress != null) {
      progress.popState();
    }
    myIsLocked = true;
    myImplementation.markSourcesAsCurrentInInitialization();
  }

  public synchronized void rootsChanged(ModuleRootEvent event) {
    refreshRoots();
    if (myTracker.isRefreshInProgress()) return;
    if (myRefreshRootsOperation != null) {
      LOG.assertTrue(false, myRefreshRootsOperation.toString());
    }
    myRefreshRootsOperation = new DelayedSyncOperation(myProject, this, LocalVcsBundle.message("operation.name.refreshing.roots")) {
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
    myRefreshRootsOperation.setLvcsAction(startExternalChangesAction());
    runSynchronizationUsing(myRefreshRootsOperation);
    myTracker.getRefreshUpdater().setOriginal(myRefreshRootsOperation);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    unregisterAll();
    shutdown();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public synchronized boolean isAvailable() {
    return myIsLocked;
  }

  public synchronized LvcsAction getAction() {
    return myAction;
  }

  public void reportLvcsHistoryIsCorrupted(final String message) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("Local history is corrupt and will be rebuilt: \n" + message);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showMessageDialog(LocalVcsBundle.message("message.text.local.history.corrupt.with.message", message),
                                   LocalVcsBundle.message("message.title.local.history.corrupt"), Messages.getWarningIcon());
      }
    }, ModalityState.NON_MMODAL);
    Runnable rebuildAction = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            clearAndRecreateLocation();
            resynchronizeRoots();
          }
        }, LocalVcsBundle.message("progress.title.rebuilding.local.history"), false, myProject);
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
    myImplementation.clear(myVcsLocation);
    FileUtil.delete(myVcsLocation);
  }

  public synchronized File getVcsLocation() {
    return myVcsLocation;
  }

  public synchronized OldLvcsImplemetation getImplementation() {
    return myImplementation;
  }

  public synchronized LvcsFileRevision addFile(final LvcsDirectoryRevision dir1, final VirtualFileInfo virtualFileInfoOnVirtualFile) {
    return myImplementation.addFile(dir1, virtualFileInfoOnVirtualFile);
  }

  public synchronized LvcsFileRevision commitFile(final LvcsFileRevision file, final VirtualFileInfo virtualFileInfo) {
    return myImplementation.commitFile(file, virtualFileInfo);
  }

  public synchronized void checkConsistency(final boolean shouldntContainScheduledForRemoval) {
    myImplementation.checkConsistency(shouldntContainScheduledForRemoval);
  }

  private static boolean isValidFileType(final FileType type) {
    return type != StdFileTypes.IDEA_WORKSPACE && type != StdFileTypes.IDEA_PROJECT && type != StdFileTypes.IDEA_MODULE && !type.isBinary();
  }

  private File findProjectVcsLocation(VirtualFile projectFile) {
    String projectName = projectFile == null ? Integer.toHexString(myProject.hashCode()) : projectFile.getName();
    String projectPath = projectFile == null ? "" : projectFile.getPath();
    return new File(new File(PathManager.getSystemPath(), VCS_DIR), createUniqueName(projectName, projectPath));
  }

  private static String createUniqueName(String name, String path) {
    return name.replace('.', '_') + "." + Integer.toHexString(path.replace('/', File.separatorChar).hashCode());
  }

}
