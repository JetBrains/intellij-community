/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.jetbrains.idea.svn;

import com.intellij.ide.FrameStateListener;
import com.intellij.ide.FrameStateManager;
import com.intellij.idea.RareLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsAnnotationCachedProxy;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.jetbrains.idea.svn.annotate.SvnAnnotationProvider;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;
import org.jetbrains.idea.svn.branchConfig.SvnLoadedBranchesStorage;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnExecutableChecker;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.LoadedRevisionsCache;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.info.InfoConsumer;
import org.jetbrains.idea.svn.integrate.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.jetbrains.idea.svn.rollback.SvnRollbackEnvironment;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.jetbrains.idea.svn.svnkit.SvnKitManager;
import org.jetbrains.idea.svn.update.SvnIntegrateEnvironment;
import org.jetbrains.idea.svn.update.SvnUpdateEnvironment;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Collections.emptyList;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class SvnVcs extends AbstractVcs<CommittedChangeList> {
  private static final String DO_NOT_LISTEN_TO_WC_DB = "svn.do.not.listen.to.wc.db";
  private static final Logger REFRESH_LOG = Logger.getInstance("#svn_refresh");
  public static boolean ourListenToWcDb = !Boolean.getBoolean(DO_NOT_LISTEN_TO_WC_DB);

  private static final Logger LOG = wrapLogger(Logger.getInstance("org.jetbrains.idea.svn.SvnVcs"));
  @NonNls public static final String VCS_NAME = "svn";
  public static final String VCS_DISPLAY_NAME = "Subversion";

  private static final VcsKey ourKey = createKey(VCS_NAME);
  public static final Topic<Runnable> WC_CONVERTED = new Topic<>("WC_CONVERTED", Runnable.class);
  private final Map<String, Map<String, Pair<PropertyValue, Trinity<Long, Long, Long>>>> myPropertyCache =
    createSoftMap();

  @NotNull private final SvnConfiguration myConfiguration;
  private final SvnEntriesFileListener myEntriesFileListener;

  private CheckinEnvironment myCheckinEnvironment;
  private RollbackEnvironment myRollbackEnvironment;
  private UpdateEnvironment mySvnUpdateEnvironment;
  private UpdateEnvironment mySvnIntegrateEnvironment;
  private AnnotationProvider myAnnotationProvider;
  private DiffProvider mySvnDiffProvider;
  private final VcsShowConfirmationOption myAddConfirmation;
  private final VcsShowConfirmationOption myDeleteConfirmation;
  private EditFileProvider myEditFilesProvider;
  private SvnCommittedChangesProvider myCommittedChangesProvider;
  private final VcsShowSettingOption myCheckoutOptions;

  private ChangeProvider myChangeProvider;
  private MergeProvider myMergeProvider;

  private final SvnChangelistListener myChangeListListener;

  private SvnCopiesRefreshManager myCopiesRefreshManager;
  private SvnFileUrlMappingImpl myMapping;
  private final MyFrameStateListener myFrameStateListener;

  //Consumer<Boolean>
  public static final Topic<Consumer> ROOTS_RELOADED = new Topic<>("ROOTS_RELOADED", Consumer.class);
  private VcsListener myVcsListener;

  private SvnBranchPointsCalculator mySvnBranchPointsCalculator;

  private final RootsToWorkingCopies myRootsToWorkingCopies;
  private final SvnAuthenticationNotifier myAuthNotifier;
  private final SvnLoadedBranchesStorage myLoadedBranchesStorage;

  private final SvnExecutableChecker myChecker;

  private SvnCheckoutProvider myCheckoutProvider;

  @NotNull private final ClientFactory cmdClientFactory;
  @NotNull private final ClientFactory svnKitClientFactory;
  @NotNull private final SvnKitManager svnKitManager;

  private final boolean myLogExceptions;

  public SvnVcs(@NotNull Project project, MessageBus bus, SvnConfiguration svnConfiguration, final SvnLoadedBranchesStorage storage) {
    super(project, VCS_NAME);

    myLoadedBranchesStorage = storage;
    myRootsToWorkingCopies = new RootsToWorkingCopies(this);
    myConfiguration = svnConfiguration;
    myAuthNotifier = new SvnAuthenticationNotifier(this);

    cmdClientFactory = new CmdClientFactory(this);
    svnKitClientFactory = new SvnKitClientFactory(this);
    svnKitManager = new SvnKitManager(this);

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    myAddConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    myDeleteConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);
    myCheckoutOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);

    if (myProject.isDefault()) {
      myChangeListListener = null;
      myEntriesFileListener = null;
    }
    else {
      myEntriesFileListener = new SvnEntriesFileListener(project);
      upgradeIfNeeded(bus);

      myChangeListListener = new SvnChangelistListener(this);

      myVcsListener = () -> invokeRefreshSvnRoots();
    }

    myFrameStateListener = project.isDefault() ? null : new MyFrameStateListener(ChangeListManager.getInstance(project),
                                                                                 VcsDirtyScopeManager.getInstance(project));
    myChecker = new SvnExecutableChecker(this);

    Application app = ApplicationManager.getApplication();
    myLogExceptions = app != null && (app.isInternal() || app.isUnitTestMode());
  }

  public void postStartup() {
    if (myProject.isDefault()) return;
    myCopiesRefreshManager = new SvnCopiesRefreshManager((SvnFileUrlMappingImpl)getSvnFileUrlMapping());
    if (!myConfiguration.isCleanupRun()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        cleanup17copies();
        myConfiguration.setCleanupRun(true);
      }, ModalityState.NON_MODAL, myProject.getDisposed());
    }
    else {
      invokeRefreshSvnRoots();
    }
  }

  /**
   * TODO: This seems to be related to some issues when upgrading from 1.6 to 1.7. So it is not currently required for 1.8 and later
   * TODO: formats. And should be removed when 1.6 working copies are no longer supported by IDEA.
   */
  private void cleanup17copies() {
    Runnable callCleanupWorker = () -> {
      if (myProject.isDisposed()) return;
      new CleanupWorker(this, emptyList()) {
        @Override
        protected void fillRoots() {
          for (WCInfo info : getAllWcInfos()) {
            if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(info.getFormat())) {
              VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(info.getRootInfo().getIoFile());
              if (file == null) {
                LOG.info("Wasn't able to find virtual file for wc root: " + info.getPath());
              }
              else {
                myRoots.add(file);
              }
            }
          }
        }
      }.execute();
    };

    myCopiesRefreshManager.waitRefresh(() -> ApplicationManager.getApplication().invokeLater(callCleanupWorker));
  }

  public boolean checkCommandLineVersion() {
    return getFactory() != cmdClientFactory || myChecker.checkExecutableAndNotifyIfNeeded();
  }

  public void invokeRefreshSvnRoots() {
    if (REFRESH_LOG.isDebugEnabled()) {
      REFRESH_LOG.debug("refresh: ", new Throwable());
    }
    if (myCopiesRefreshManager != null) {
      myCopiesRefreshManager.asynchRequest();
    }
  }

  private void upgradeIfNeeded(final MessageBus bus) {
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(ChangeListManagerImpl.LISTS_LOADED, lists -> {
      if (lists.isEmpty()) return;
      try {
        ChangeListManager.getInstance(myProject).setReadOnly(LocalChangeList.DEFAULT_NAME, true);

        if (!myConfiguration.changeListsSynchronized()) {
          processChangeLists(lists);
        }
      }
      catch (ProcessCanceledException e) {
        //
      }
      finally {
        myConfiguration.upgrade();
      }

      connection.disconnect();
    });
  }

  public void processChangeLists(final List<LocalChangeList> lists) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstanceChecked(myProject);
    plVcsManager.startBackgroundVcsOperation();
    try {
      for (LocalChangeList list : lists) {
        if (!list.isDefault()) {
          final Collection<Change> changes = list.getChanges();
          for (Change change : changes) {
            correctListForRevision(plVcsManager, change.getBeforeRevision(), list.getName());
            correctListForRevision(plVcsManager, change.getAfterRevision(), list.getName());
          }
        }
      }
    }
    finally {
      final Application appManager = ApplicationManager.getApplication();
      if (appManager.isDispatchThread()) {
        appManager.executeOnPooledThread(() -> plVcsManager.stopBackgroundVcsOperation());
      }
      else {
        plVcsManager.stopBackgroundVcsOperation();
      }
    }
  }

  private void correctListForRevision(@NotNull final ProjectLevelVcsManager plVcsManager,
                                      @Nullable final ContentRevision revision,
                                      @NotNull final String name) {
    if (revision != null) {
      final FilePath path = revision.getFile();
      final AbstractVcs vcs = plVcsManager.getVcsFor(path);
      if (vcs != null && VCS_NAME.equals(vcs.getName())) {
        try {
          getFactory(path.getIOFile()).createChangeListClient().add(name, path.getIOFile(), null);
        }
        catch (VcsException e) {
          // left in default list
        }
      }
    }
  }

  @Override
  public void activate() {
    if (!myProject.isDefault()) {
      ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);
      myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, myVcsListener);
    }

    SvnApplicationSettings.getInstance().svnActivated();
    if (myEntriesFileListener != null) {
      VirtualFileManager.getInstance().addVirtualFileListener(myEntriesFileListener);
    }
    // this will initialize its inner listener for committed changes upload
    LoadedRevisionsCache.getInstance(myProject);
    FrameStateManager.getInstance().addListener(myFrameStateListener);

    myAuthNotifier.init();
    mySvnBranchPointsCalculator = new SvnBranchPointsCalculator(this);

    svnKitManager.activate();

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      checkCommandLineVersion();
    }

    // do one time after project loaded
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
      postStartup();

      // for IDEA, it takes 2 minutes - and anyway this can be done in background, no sense...
      // once it could be mistaken about copies for 2 minutes on start...

      /*if (! myMapping.getAllWcInfos().isEmpty()) {
        invokeRefreshSvnRoots();
        return;
      }
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          myCopiesRefreshManager.getCopiesRefresh().ensureInit();
        }
      }, SvnBundle.message("refreshing.working.copies.roots.progress.text"), true, myProject);*/
    });

    myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, myRootsToWorkingCopies);

    myLoadedBranchesStorage.activate();
  }

  public static Logger wrapLogger(final Logger logger) {
    return RareLogger.wrap(logger, Boolean.getBoolean("svn.logger.fairsynch"), new SvnExceptionLogFilter());
  }

  public RootsToWorkingCopies getRootsToWorkingCopies() {
    return myRootsToWorkingCopies;
  }

  public SvnAuthenticationNotifier getAuthNotifier() {
    return myAuthNotifier;
  }

  @Override
  public void deactivate() {
    FrameStateManager.getInstance().removeListener(myFrameStateListener);

    if (myEntriesFileListener != null) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myEntriesFileListener);
    }
    SvnApplicationSettings.getInstance().svnDeactivated();
    if (myCommittedChangesProvider != null) {
      myCommittedChangesProvider.deactivate();
    }
    if (myChangeListListener != null && !myProject.isDefault()) {
      ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListListener);
    }
    myRootsToWorkingCopies.clear();

    myAuthNotifier.stop();
    myAuthNotifier.clear();

    mySvnBranchPointsCalculator.deactivate();
    mySvnBranchPointsCalculator = null;
    myLoadedBranchesStorage.deactivate();
  }

  public VcsShowConfirmationOption getAddConfirmation() {
    return myAddConfirmation;
  }

  public VcsShowConfirmationOption getDeleteConfirmation() {
    return myDeleteConfirmation;
  }

  public VcsShowSettingOption getCheckoutOptions() {
    return myCheckoutOptions;
  }

  @Override
  public EditFileProvider getEditFileProvider() {
    if (myEditFilesProvider == null) {
      myEditFilesProvider = new SvnEditFileProvider(this);
    }
    return myEditFilesProvider;
  }

  @Override
  @NotNull
  public ChangeProvider getChangeProvider() {
    if (myChangeProvider == null) {
      myChangeProvider = new SvnChangeProvider(this);
    }
    return myChangeProvider;
  }

  @Override
  public UpdateEnvironment getIntegrateEnvironment() {
    if (mySvnIntegrateEnvironment == null) {
      mySvnIntegrateEnvironment = new SvnIntegrateEnvironment(this);
    }
    return mySvnIntegrateEnvironment;
  }

  @Override
  public UpdateEnvironment createUpdateEnvironment() {
    if (mySvnUpdateEnvironment == null) {
      mySvnUpdateEnvironment = new SvnUpdateEnvironment(this);
    }
    return mySvnUpdateEnvironment;
  }

  @Override
  public String getDisplayName() {
    return VCS_DISPLAY_NAME;
  }

  @Override
  public Configurable getConfigurable() {
    return null;
  }

  @NotNull
  public SvnConfiguration getSvnConfiguration() {
    return myConfiguration;
  }

  public static SvnVcs getInstance(@NotNull Project project) {
    return (SvnVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME);
  }

  @Override
  @NotNull
  public CheckinEnvironment createCheckinEnvironment() {
    if (myCheckinEnvironment == null) {
      myCheckinEnvironment = new SvnCheckinEnvironment(this);
    }
    return myCheckinEnvironment;
  }

  @Override
  @NotNull
  public RollbackEnvironment createRollbackEnvironment() {
    if (myRollbackEnvironment == null) {
      myRollbackEnvironment = new SvnRollbackEnvironment(this);
    }
    return myRollbackEnvironment;
  }

  @Override
  public VcsHistoryProvider getVcsHistoryProvider() {
    // no heavy state, but it would be useful to have place to keep state in -> do not reuse instance
    return new SvnHistoryProvider(this);
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return getVcsHistoryProvider();
  }

  @Override
  public AnnotationProvider getAnnotationProvider() {
    if (myAnnotationProvider == null) {
      myAnnotationProvider = new SvnAnnotationProvider(this);
    }
    return new VcsAnnotationCachedProxy(this, myAnnotationProvider);
  }

  @Override
  public DiffProvider getDiffProvider() {
    if (mySvnDiffProvider == null) {
      mySvnDiffProvider = new SvnDiffProvider(this);
    }
    return mySvnDiffProvider;
  }

  private static Trinity<Long, Long, Long> getTimestampForPropertiesChange(final File ioFile, final boolean isDir) {
    final File dir = isDir ? ioFile : ioFile.getParentFile();
    final String relPath = SVNAdminUtil.getPropPath(ioFile.getName(), isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE, false);
    final String relPathBase = SVNAdminUtil.getPropBasePath(ioFile.getName(), isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE, false);
    final String relPathRevert = SVNAdminUtil.getPropRevertPath(ioFile.getName(), isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE, false);
    return new Trinity<>(new File(dir, relPath).lastModified(), new File(dir, relPathBase).lastModified(),
                         new File(dir, relPathRevert).lastModified());
  }

  private static boolean trinitiesEqual(final Trinity<Long, Long, Long> t1, final Trinity<Long, Long, Long> t2) {
    if (t2.first == 0 && t2.second == 0 && t2.third == 0) return false;
    return t1.equals(t2);
  }

  @Nullable
  public PropertyValue getPropertyWithCaching(final VirtualFile file, final String propName) throws VcsException {
    Map<String, Pair<PropertyValue, Trinity<Long, Long, Long>>> cachedMap = myPropertyCache.get(keyForVf(file));
    final Pair<PropertyValue, Trinity<Long, Long, Long>> cachedValue = cachedMap == null ? null : cachedMap.get(propName);

    final File ioFile = virtualToIoFile(file);
    final Trinity<Long, Long, Long> tsTrinity = getTimestampForPropertiesChange(ioFile, file.isDirectory());

    if (cachedValue != null) {
      // zero means that a file was not found
      if (trinitiesEqual(cachedValue.getSecond(), tsTrinity)) {
        return cachedValue.getFirst();
      }
    }

    PropertyClient client = getFactory(ioFile).createPropertyClient();
    final PropertyValue value = client.getProperty(SvnTarget.fromFile(ioFile, SVNRevision.WORKING), propName, false, SVNRevision.WORKING);

    if (cachedMap == null) {
      cachedMap = new HashMap<>();
      myPropertyCache.put(keyForVf(file), cachedMap);
    }

    cachedMap.put(propName, Pair.create(value, tsTrinity));

    return value;
  }

  @Override
  public boolean fileExistsInVcs(FilePath path) {
    File file = path.getIOFile();
    try {
      Status status = getFactory(file).createStatusClient().doStatus(file, false);
      if (status != null) {
        return status.is(StatusType.STATUS_ADDED)
               ? status.isCopied()
               : !status.is(StatusType.STATUS_UNVERSIONED, StatusType.STATUS_IGNORED, StatusType.STATUS_OBSTRUCTED);
      }
    }
    catch (SvnBindException e) {
      LOG.info(e);
    }
    return false;
  }

  @Override
  public boolean fileIsUnderVcs(@NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    return file != null && SvnStatusUtil.isUnderControl(this, file);
  }

  @Nullable
  public Info getInfo(@NotNull SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SvnBindException {
    return getFactory().createInfoClient().doInfo(SvnTarget.fromURL(url, pegRevision), revision);
  }

  @Nullable
  public Info getInfo(@NotNull SVNURL url, SVNRevision revision) throws SvnBindException {
    return getInfo(url, SVNRevision.UNDEFINED, revision);
  }

  @Nullable
  public Info getInfo(@NotNull final VirtualFile file) {
    return getInfo(virtualToIoFile(file));
  }

  @Nullable
  public Info getInfo(@NotNull String path) {
    return getInfo(new File(path));
  }

  @Nullable
  public Info getInfo(@NotNull File ioFile) {
    return getInfo(ioFile, SVNRevision.UNDEFINED);
  }

  public void collectInfo(@NotNull Collection<File> files, @Nullable InfoConsumer handler) {
    File first = ContainerUtil.getFirstItem(files);

    if (first != null) {
      ClientFactory factory = getFactory(first);

      try {
        if (factory instanceof CmdClientFactory) {
          factory.createInfoClient().doInfo(files, handler);
        }
        else {
          // TODO: Generally this should be moved in SvnKit info client implementation.
          // TODO: Currently left here to have exception logic as in handleInfoException to be applied for each file separately.
          for (File file : files) {
            Info info = getInfo(file);
            if (handler != null) {
              handler.consume(info);
            }
          }
        }
      }
      catch (SVNException e) {
        handleInfoException(new SvnBindException(e));
      }
      catch (SvnBindException e) {
        handleInfoException(e);
      }
    }
  }

  @Nullable
  public Info getInfo(@NotNull File ioFile, @NotNull SVNRevision revision) {
    Info result = null;

    try {
      result = getFactory(ioFile).createInfoClient().doInfo(ioFile, revision);
    }
    catch (SvnBindException e) {
      handleInfoException(e);
    }

    return result;
  }

  private void handleInfoException(@NotNull SvnBindException e) {
    if (!myLogExceptions ||
        SvnUtil.isUnversionedOrNotFound(e) ||
        // do not log working copy format vs client version inconsistencies as errors
        e.contains(SVNErrorCode.WC_UNSUPPORTED_FORMAT) ||
        e.contains(SVNErrorCode.WC_UPGRADE_REQUIRED)) {
      LOG.debug(e);
    }
    else {
      LOG.error(e);
    }
  }

  @NotNull
  public WorkingCopyFormat getWorkingCopyFormat(@NotNull File ioFile) {
    return getWorkingCopyFormat(ioFile, true);
  }

  @NotNull
  public WorkingCopyFormat getWorkingCopyFormat(@NotNull File ioFile, boolean useMapping) {
    WorkingCopyFormat format = WorkingCopyFormat.UNKNOWN;

    if (useMapping) {
      RootUrlInfo rootInfo = getSvnFileUrlMapping().getWcRootForFilePath(ioFile);
      format = rootInfo != null ? rootInfo.getFormat() : WorkingCopyFormat.UNKNOWN;
    }

    return WorkingCopyFormat.UNKNOWN.equals(format) ? SvnFormatSelector.findRootAndGetFormat(ioFile) : format;
  }

  public boolean isWcRoot(@NotNull FilePath filePath) {
    boolean isWcRoot = false;
    VirtualFile file = filePath.getVirtualFile();
    WorkingCopy wcRoot = file != null ? myRootsToWorkingCopies.getWcRoot(file) : null;
    if (wcRoot != null) {
      isWcRoot = wcRoot.getFile().getAbsolutePath().equals(filePath.getPath());
    }
    return isWcRoot;
  }

  @Override
  public FileStatus[] getProvidedStatuses() {
    return new FileStatus[]{SvnFileStatus.EXTERNAL,
      SvnFileStatus.OBSTRUCTED,
      SvnFileStatus.REPLACED};
  }


  @Override
  @NotNull
  public CommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> getCommittedChangesProvider() {
    if (myCommittedChangesProvider == null) {
      myCommittedChangesProvider = new SvnCommittedChangesProvider(this);
    }
    return myCommittedChangesProvider;
  }

  @Nullable
  @Override
  public VcsRevisionNumber parseRevisionNumber(final String revisionNumberString) {
    final SVNRevision revision = SVNRevision.parse(revisionNumberString);
    if (revision.equals(SVNRevision.UNDEFINED)) {
      return null;
    }
    return new SvnRevisionNumber(revision);
  }

  @Override
  public String getRevisionPattern() {
    return ourIntegerPattern;
  }

  @Override
  public boolean isVersionedDirectory(final VirtualFile dir) {
    return SvnUtil.seemsLikeVersionedDir(dir);
  }

  @NotNull
  public SvnFileUrlMapping getSvnFileUrlMapping() {
    if (myMapping == null) {
      myMapping = SvnFileUrlMappingImpl.getInstance(myProject);
    }
    return myMapping;
  }

  /**
   * Returns real working copies roots - if there is <Project Root> -> Subversion setting,
   * and there is one working copy, will return one root
   */
  public List<WCInfo> getAllWcInfos() {
    final SvnFileUrlMapping urlMapping = getSvnFileUrlMapping();

    final List<RootUrlInfo> infoList = urlMapping.getAllWcInfos();
    final List<WCInfo> infos = new ArrayList<>();
    for (RootUrlInfo info : infoList) {
      final File file = info.getIoFile();

      infos.add(new WCInfo(info, SvnUtil.isWorkingCopyRoot(file), SvnUtil.getDepth(this, file)));
    }
    return infos;
  }

  public List<WCInfo> getWcInfosWithErrors() {
    List<WCInfo> result = new ArrayList<>(getAllWcInfos());

    for (RootUrlInfo info : getSvnFileUrlMapping().getErrorRoots()) {
      result.add(new WCInfo(info, SvnUtil.isWorkingCopyRoot(info.getIoFile()), Depth.UNKNOWN));
    }

    return result;
  }

  @Override
  public RootsConvertor getCustomConvertor() {
    if (myProject.isDefault()) return null;
    return getSvnFileUrlMapping();
  }

  @Override
  public MergeProvider getMergeProvider() {
    if (myMergeProvider == null) {
      myMergeProvider = new SvnMergeProvider(myProject);
    }
    return myMergeProvider;
  }

  private static String keyForVf(final VirtualFile vf) {
    return vf.getUrl();
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  @NotNull
  @Override
  public <S> List<S> filterUniqueRoots(@NotNull List<S> in, @NotNull Function<S, VirtualFile> convertor) {
    if (in.size() <= 1) return in;

    List<MyPair<S>> infos = newArrayList();
    List<S> notMatched = newArrayList();
    for (S s : in) {
      VirtualFile vf = convertor.apply(s);
      if (vf == null) continue;

      File ioFile = virtualToIoFile(vf);
      SVNURL url = getSvnFileUrlMapping().getUrlForFile(ioFile);
      if (url == null) {
        url = SvnUtil.getUrl(this, ioFile);
        if (url == null) {
          notMatched.add(s);
          continue;
        }
      }
      infos.add(new MyPair<>(vf, url.toString(), s));
    }
    List<MyPair<S>> filtered = new UniqueRootsFilter().filter(infos);
    List<S> converted = map(filtered, MyPair::getSrc);

    // potential bug is here: order is not kept. but seems it only occurs for cases where result is sorted after filtering so ok
    return concat(converted, notMatched);
  }

  private static class MyPair<T> implements RootUrlPair {
    @NotNull private final VirtualFile myFile;
    @NotNull private final String myUrl;
    private final T mySrc;

    private MyPair(@NotNull VirtualFile file, @NotNull String url, T src) {
      myFile = file;
      myUrl = url;
      mySrc = src;
    }

    public T getSrc() {
      return mySrc;
    }

    @NotNull
    @Override
    public VirtualFile getVirtualFile() {
      return myFile;
    }

    @NotNull
    @Override
    public String getUrl() {
      return myUrl;
    }
  }

  private static class MyFrameStateListener extends FrameStateListener.Adapter {
    private final ChangeListManager myClManager;
    private final VcsDirtyScopeManager myDirtyScopeManager;

    private MyFrameStateListener(ChangeListManager clManager, VcsDirtyScopeManager dirtyScopeManager) {
      myClManager = clManager;
      myDirtyScopeManager = dirtyScopeManager;
    }

    @Override
    public void onFrameActivated() {
      final List<VirtualFile> folders = ((ChangeListManagerImpl)myClManager).getLockedFolders();
      if (!folders.isEmpty()) {
        myDirtyScopeManager.filesDirty(null, folders);
      }
    }
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public boolean isVcsBackgroundOperationsAllowed(@NotNull VirtualFile root) {
    ClientFactory factory = getFactory(virtualToIoFile(root));

    return ThreeState.YES.equals(myAuthNotifier.isAuthenticatedFor(root, factory == cmdClientFactory ? factory : null));
  }

  public SvnBranchPointsCalculator getSvnBranchPointsCalculator() {
    return mySvnBranchPointsCalculator;
  }

  @Override
  public boolean areDirectoriesVersionedItems() {
    return true;
  }

  @Override
  public CheckoutProvider getCheckoutProvider() {
    if (myCheckoutProvider == null) {
      myCheckoutProvider = new SvnCheckoutProvider();
    }
    return myCheckoutProvider;
  }

  @NotNull
  public SvnKitManager getSvnKitManager() {
    return svnKitManager;
  }

  @NotNull
  private WorkingCopyFormat getProjectRootFormat() {
    VirtualFile baseDir = myProject.getBaseDir();

    return baseDir != null ? getWorkingCopyFormat(virtualToIoFile(baseDir)) : WorkingCopyFormat.UNKNOWN;
  }

  /**
   * Detects appropriate client factory based on project root directory working copy format.
   * <p>
   * Try to avoid usages of this method (for now) as it could not correctly for all cases
   * detect svn 1.8 working copy format to guarantee command line client.
   * <p>
   * For instance, when working copies of several formats are presented in project
   * (though it seems to be rather unlikely case).
   *
   * @return
   */
  @NotNull
  public ClientFactory getFactory() {
    return getFactory(getProjectRootFormat(), false);
  }

  @NotNull
  public ClientFactory getFactory(@NotNull WorkingCopyFormat format) {
    return getFactory(format, false);
  }

  @NotNull
  public ClientFactory getFactory(@NotNull File file) {
    return getFactory(file, true);
  }

  @NotNull
  public ClientFactory getFactory(@NotNull File file, boolean useMapping) {
    return getFactory(getWorkingCopyFormat(file, useMapping), true);
  }

  @NotNull
  private ClientFactory getFactory(@NotNull WorkingCopyFormat format, boolean useProjectRootForUnknown) {
    boolean is18OrGreater = format.isOrGreater(WorkingCopyFormat.ONE_DOT_EIGHT);
    boolean isUnknown = WorkingCopyFormat.UNKNOWN.equals(format);

    return is18OrGreater
           ? cmdClientFactory
           : (!isUnknown && !isSupportedByCommandLine(format)
              ? svnKitClientFactory
              : (useProjectRootForUnknown && isUnknown ? getFactory() : getFactoryFromSettings()));
  }

  @NotNull
  public ClientFactory getFactory(@NotNull SvnTarget target) {
    return target.isFile() ? getFactory(target.getFile()) : getFactory();
  }

  @NotNull
  public ClientFactory getFactoryFromSettings() {
    return myConfiguration.isCommandLine() ? cmdClientFactory : svnKitClientFactory;
  }

  @NotNull
  public ClientFactory getOtherFactory() {
    return myConfiguration.isCommandLine() ? svnKitClientFactory : cmdClientFactory;
  }

  @NotNull
  public ClientFactory getOtherFactory(@NotNull ClientFactory factory) {
    return factory.equals(cmdClientFactory) ? svnKitClientFactory : cmdClientFactory;
  }

  @NotNull
  public ClientFactory getCommandLineFactory() {
    return cmdClientFactory;
  }

  @NotNull
  public ClientFactory getSvnKitFactory() {
    return svnKitClientFactory;
  }

  @NotNull
  public WorkingCopyFormat getLowestSupportedFormatForCommandLine() {
    WorkingCopyFormat result;

    try {
      result = WorkingCopyFormat.from(CmdVersionClient.parseVersion(Registry.stringValue("svn.lowest.supported.format.for.command.line")));
    }
    catch (SvnBindException ignore) {
      result = WorkingCopyFormat.ONE_DOT_SEVEN;
    }

    return result;
  }

  public boolean isSupportedByCommandLine(@NotNull WorkingCopyFormat format) {
    return format.isOrGreater(getLowestSupportedFormatForCommandLine());
  }

  public boolean is16SupportedByCommandLine() {
    return isSupportedByCommandLine(WorkingCopyFormat.ONE_DOT_SIX);
  }
}
