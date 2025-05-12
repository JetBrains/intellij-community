// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.application.Topics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.actions.ExclusiveBackgroundVcsAction;
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
import org.jetbrains.idea.svn.update.SvnIntegrateEnvironment;
import org.jetbrains.idea.svn.update.SvnUpdateEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterPath;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getBeforePath;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static com.intellij.vcsUtil.VcsUtil.isFileForVcs;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;

public final class SvnVcs extends AbstractVcs {
  private static final Logger LOG = new SvnFilteringExceptionLogger(Logger.getInstance(SvnVcs.class));
  private static final Logger REFRESH_LOG = Logger.getInstance("#svn_refresh");

  public static final @NonNls @NotNull String VCS_NAME = "svn";
  public static final @NlsSafe @NotNull String VCS_DISPLAY_NAME = "Subversion";
  private static final @NlsSafe @NotNull String VCS_SHORT_DISPLAY_NAME = "SVN";

  private static final VcsKey ourKey = createKey(VCS_NAME);

  @Topic.ProjectLevel
  @Topic.AppLevel
  public static final Topic<Runnable> WC_CONVERTED = new Topic<>("WC_CONVERTED", Runnable.class);

  private CheckinEnvironment myCheckinEnvironment;
  private RollbackEnvironment myRollbackEnvironment;
  private UpdateEnvironment mySvnUpdateEnvironment;
  private UpdateEnvironment mySvnIntegrateEnvironment;
  private AnnotationProvider myAnnotationProvider;
  private DiffProvider mySvnDiffProvider;
  private EditFileProvider myEditFilesProvider;
  private SvnCommittedChangesProvider myCommittedChangesProvider;

  private ChangeProvider myChangeProvider;
  private MergeProvider myMergeProvider;

  private final AtomicReference<Disposable> myDisposable = new AtomicReference<>();

  //Consumer<Boolean>
  public static final Topic<Consumer> ROOTS_RELOADED = new Topic<>("ROOTS_RELOADED", Consumer.class);

  private SvnBranchPointsCalculator mySvnBranchPointsCalculator;
  private SvnCheckoutProvider myCheckoutProvider;

  private final @NotNull ClientFactory cmdClientFactory;

  private final boolean myLogExceptions;

  public SvnVcs(@NotNull Project project) {
    super(project, VCS_NAME);

    cmdClientFactory = new CmdClientFactory(this);

    Application app = ApplicationManager.getApplication();
    myLogExceptions = app != null && (app.isInternal() || app.isUnitTestMode());
  }

  private void postStartup() {
    if (myProject.isDefault()) return;

    if (!getSvnConfiguration().isCleanupRun()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        cleanup17copies();
        getSvnConfiguration().setCleanupRun(true);
      }, ModalityState.nonModal(), myProject.getDisposed());
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

    getSvnFileUrlMappingImpl().scheduleRefresh(() -> ApplicationManager.getApplication().invokeLater(callCleanupWorker));
  }

  public boolean checkCommandLineVersion() {
    return myProject.getService(SvnExecutableChecker.class).checkExecutableAndNotifyIfNeeded();
  }

  public void invokeRefreshSvnRoots() {
    if (REFRESH_LOG.isDebugEnabled()) {
      REFRESH_LOG.debug("refresh: ", new Throwable());
    }
    getSvnFileUrlMappingImpl().scheduleRefresh();
  }

  private void setupChangeLists() {
    ChangeListManager.getInstance(myProject).setReadOnly(LocalChangeList.getDefaultName(), true);

    if (!getSvnConfiguration().changeListsSynchronized()) {
      List<LocalChangeList> changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
      ExclusiveBackgroundVcsAction.run(myProject, () -> synchronizeToNativeChangeLists(changeLists));
    }
    getSvnConfiguration().upgrade();
  }

  public void synchronizeToNativeChangeLists(@NotNull List<? extends LocalChangeList> lists) {
    for (LocalChangeList list : lists) {
      if (list.isDefault()) continue;

      for (Change change : list.getChanges()) {
        setNativeChangeList(getBeforePath(change), list.getName());
        setNativeChangeList(getAfterPath(change), list.getName());
      }
    }
  }

  private void setNativeChangeList(@Nullable FilePath path, @NotNull String changeListName) {
    if (path == null) return;
    if (!isFileForVcs(path, myProject, this)) return;

    try {
      getFactory(path.getIOFile()).createChangeListClient().add(changeListName, path.getIOFile(), null);
    }
    catch (VcsException e) {
      // left in default list
    }
  }

  @Override
  public void activate() {
    Disposable disposable = Disposer.newDisposable();
    // do not leak Project if 'deactivate' is never called
    Disposer.register(SvnDisposable.getInstance(myProject), disposable);
    // workaround the race between 'activate' and 'deactivate'
    Disposable oldDisposable = myDisposable.getAndSet(disposable);
    if (oldDisposable != null) Disposer.dispose(oldDisposable);


    MessageBusConnection busConnection = myProject.getMessageBus().connect();
    busConnection.subscribe(ChangeListListener.TOPIC, new SvnChangelistListener(this));
    busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> invokeRefreshSvnRoots());

    SvnFileSystemListener fileOperationsHandler = new SvnFileSystemListener(this);
    Disposer.register(disposable, fileOperationsHandler);

    VirtualFileManager.getInstance().addVirtualFileListener(new SvnEntriesFileListener(myProject), disposable);

    // this will initialize its inner listener for committed changes upload
    LoadedRevisionsCache.getInstance(myProject);
    Topics.subscribe(ApplicationActivationListener.TOPIC, disposable,
                     new MyFrameStateListener(ChangeListManager.getInstance(myProject), VcsDirtyScopeManager.getInstance(myProject)));

    mySvnBranchPointsCalculator = new SvnBranchPointsCalculator(this);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      BackgroundTaskUtil.executeOnPooledThread(disposable, () -> checkCommandLineVersion());
    }

    RootsToWorkingCopies rootsToWorkingCopies = RootsToWorkingCopies.getInstance(myProject);
    SvnAuthenticationNotifier svnAuthenticationNotifier = SvnAuthenticationNotifier.getInstance(myProject);
    SvnLoadedBranchesStorage svnLoadedBranchesStorage = SvnLoadedBranchesStorage.getInstance(myProject);
    svnLoadedBranchesStorage.activate();
    Disposer.register(disposable, () -> {
      rootsToWorkingCopies.clear();
      svnAuthenticationNotifier.clear();
      svnLoadedBranchesStorage.deactivate();
    });

    ProjectLevelVcsManager.getInstance(myProject).runAfterInitialization(() -> setupChangeLists());
    StartupManager.getInstance(myProject).runAfterOpened(() -> postStartup());
  }

  @Override
  public void deactivate() {
    Disposable disposable = myDisposable.getAndSet(null);
    if (disposable != null) Disposer.dispose(disposable);

    if (myCommittedChangesProvider != null) {
      myCommittedChangesProvider.deactivate();
      myCommittedChangesProvider = null;
    }

    mySvnBranchPointsCalculator.deactivate();
    mySvnBranchPointsCalculator = null;
  }

  @Override
  public EditFileProvider getEditFileProvider() {
    if (myEditFilesProvider == null) {
      myEditFilesProvider = new SvnEditFileProvider(this);
    }
    return myEditFilesProvider;
  }

  @Override
  public @NotNull ChangeProvider getChangeProvider() {
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
  public @NotNull String getDisplayName() {
    return VCS_DISPLAY_NAME;
  }

  @Override
  public @NotNull String getShortName() {
    return VCS_SHORT_DISPLAY_NAME;
  }

  @Override
  public @Nls @NotNull String getShortNameWithMnemonic() {
    return SvnBundle.message("svn.short.name.with.mnemonic");
  }

  public @NotNull SvnConfiguration getSvnConfiguration() {
    return SvnConfiguration.getInstance(myProject);
  }

  public static SvnVcs getInstance(@NotNull Project project) {
    return (SvnVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME);
  }

  @Override
  public @NotNull CheckinEnvironment createCheckinEnvironment() {
    if (myCheckinEnvironment == null) {
      myCheckinEnvironment = new SvnCheckinEnvironment(this);
    }
    return myCheckinEnvironment;
  }

  @Override
  public @NotNull RollbackEnvironment createRollbackEnvironment() {
    if (myRollbackEnvironment == null) {
      myRollbackEnvironment = new SvnRollbackEnvironment(this);
    }
    return myRollbackEnvironment;
  }

  @Override
  public @NotNull SvnHistoryProvider getVcsHistoryProvider() {
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

  @Override
  public void loadSettings() {
    super.loadSettings();

    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);
    vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);
  }

  public @Nullable PropertyValue getPropertyWithCaching(@NotNull VirtualFile file, @NotNull String propName) throws VcsException {
    // TODO Method is called in EDT - fix
    File ioFile = virtualToIoFile(file);
    PropertyClient client = getFactory(ioFile).createPropertyClient();

    return client.getProperty(Target.on(ioFile, Revision.WORKING), propName, false, Revision.WORKING);
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

  public @Nullable Info getInfo(@NotNull Url url, Revision pegRevision, Revision revision) throws SvnBindException {
    return getFactory().createInfoClient().doInfo(Target.on(url, pegRevision), revision);
  }

  public @Nullable Info getInfo(@NotNull Url url, Revision revision) throws SvnBindException {
    return getInfo(url, Revision.UNDEFINED, revision);
  }

  public @Nullable Info getInfo(final @NotNull VirtualFile file) {
    return getInfo(virtualToIoFile(file));
  }

  public @Nullable Info getInfo(@NotNull String path) {
    return getInfo(new File(path));
  }

  public @Nullable Info getInfo(@NotNull File ioFile) {
    return getInfo(ioFile, Revision.UNDEFINED);
  }

  public void collectInfo(@NotNull Collection<File> files, @Nullable InfoConsumer handler) {
    File first = getFirstItem(files);

    if (first != null) {
      try {
        getFactory(first).createInfoClient().doInfo(files, handler);
      }
      catch (SvnBindException e) {
        handleInfoException(e);
      }
    }
  }

  public @Nullable Info getInfo(@NotNull File ioFile, @NotNull Revision revision) {
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
        e.contains(ErrorCode.WC_UNSUPPORTED_FORMAT) ||
        e.contains(ErrorCode.WC_UPGRADE_REQUIRED)) {
      LOG.debug(e);
    }
    else {
      LOG.error(e);
    }
  }

  public @NotNull WorkingCopyFormat getWorkingCopyFormat(@NotNull File ioFile) {
    return getWorkingCopyFormat(ioFile, true);
  }

  public @NotNull WorkingCopyFormat getWorkingCopyFormat(@NotNull File ioFile, boolean useMapping) {
    WorkingCopyFormat format = WorkingCopyFormat.UNKNOWN;

    if (useMapping) {
      RootUrlInfo rootInfo = getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(ioFile));
      format = rootInfo != null ? rootInfo.getFormat() : WorkingCopyFormat.UNKNOWN;
    }

    return WorkingCopyFormat.UNKNOWN.equals(format) ? SvnFormatSelector.findRootAndGetFormat(ioFile) : format;
  }

  public boolean isWcRoot(@NotNull FilePath filePath) {
    boolean isWcRoot = false;
    VirtualFile file = filePath.getVirtualFile();
    WorkingCopy wcRoot = file != null ? RootsToWorkingCopies.getInstance(myProject).getWcRoot(file) : null;
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
  public @NotNull CommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> getCommittedChangesProvider() {
    if (myCommittedChangesProvider == null) {
      myCommittedChangesProvider = new SvnCommittedChangesProvider(this);
    }
    return myCommittedChangesProvider;
  }

  @Override
  public @Nullable VcsRevisionNumber parseRevisionNumber(final String revisionNumberString) {
    final Revision revision = Revision.parse(revisionNumberString);
    if (revision.equals(Revision.UNDEFINED)) {
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

  public @NotNull SvnFileUrlMapping getSvnFileUrlMapping() {
    return myProject.getService(SvnFileUrlMapping.class);
  }

  @NotNull
  public SvnFileUrlMappingImpl getSvnFileUrlMappingImpl() {
    return ((SvnFileUrlMappingImpl)getSvnFileUrlMapping());
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
  public boolean needsLegacyDefaultMappings() {
    return false;
  }

  @Override
  public MergeProvider getMergeProvider() {
    if (myMergeProvider == null) {
      myMergeProvider = new SvnMergeProvider(myProject);
    }
    return myMergeProvider;
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  @Override
  public @NotNull <S> List<S> filterUniqueRoots(@NotNull List<S> in, @NotNull Function<? super S, ? extends VirtualFile> convertor) {
    if (in.size() <= 1) return in;

    return Registry.is("svn.filter.unique.roots.by.url") ? filterUniqueByUrl(in, convertor) : filterUniqueByWorkingCopy(in, convertor);
  }

  private @NotNull <S> List<S> filterUniqueByUrl(@NotNull List<? extends S> in, @NotNull Function<? super S, ? extends VirtualFile> convertor) {
    List<MyPair<S>> infos = new ArrayList<>();
    List<S> notMatched = new ArrayList<>();
    for (S s : in) {
      VirtualFile vf = convertor.apply(s);
      if (vf == null) continue;

      File ioFile = virtualToIoFile(vf);
      Url url = getSvnFileUrlMapping().getUrlForFile(ioFile);
      if (url == null) {
        url = SvnUtil.getUrl(this, ioFile);
        if (url == null) {
          notMatched.add(s);
          continue;
        }
      }
      infos.add(new MyPair<>(vf, url, s));
    }
    List<MyPair<S>> filtered = new UniqueRootsFilter().filter(infos);
    List<S> converted = map(filtered, MyPair::getSrc);

    // potential bug is here: order is not kept. but seems it only occurs for cases where result is sorted after filtering so ok
    return concat(converted, notMatched);
  }

  private @NotNull <S> List<S> filterUniqueByWorkingCopy(@NotNull List<? extends S> in,
                                                         @NotNull Function<? super S, ? extends VirtualFile> convertor) {
    Map<VirtualFile, S> filesMap = StreamEx.of(in).<VirtualFile, S>mapToEntry(convertor, identity()).distinctKeys().toMap();
    Map<VirtualFile, List<VirtualFile>> byWorkingCopy =
      StreamEx.of(filesMap.keySet())
        .mapToEntry(
          file -> {
            RootUrlInfo wcRoot = getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(file));
            return wcRoot != null ? wcRoot.getVirtualFile() : SvnUtil.getWorkingCopyRoot(file);
          },
          identity())
        .nonNullKeys()
        .grouping();

    return EntryStream.of(byWorkingCopy)
      .flatMapToValue((workingCopy, files) -> {
        FilterDescendantVirtualFiles.filter(files);
        return files.stream();
      })
      .values()
      .map(filesMap::get)
      .toList();
  }

  private static final class MyPair<T> implements RootUrlPair {
    private final @NotNull VirtualFile myFile;
    private final @NotNull Url myUrl;
    private final T mySrc;

    private MyPair(@NotNull VirtualFile file, @NotNull Url url, T src) {
      myFile = file;
      myUrl = url;
      mySrc = src;
    }

    public T getSrc() {
      return mySrc;
    }

    @Override
    public @NotNull VirtualFile getVirtualFile() {
      return myFile;
    }

    @Override
    public @NotNull Url getUrl() {
      return myUrl;
    }
  }

  private static final class MyFrameStateListener implements ApplicationActivationListener {
    private final ChangeListManager myClManager;
    private final VcsDirtyScopeManager myDirtyScopeManager;

    private MyFrameStateListener(ChangeListManager clManager, VcsDirtyScopeManager dirtyScopeManager) {
      myClManager = clManager;
      myDirtyScopeManager = dirtyScopeManager;
    }

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
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

    ThreeState authResult =
      SvnAuthenticationNotifier.getInstance(myProject).isAuthenticatedFor(root, factory == cmdClientFactory ? factory : null);
    return ThreeState.YES.equals(authResult);
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

  /**
   * Detects appropriate client factory based on project root directory working copy format.
   * <p>
   * Try to avoid usages of this method (for now) as it could not correctly for all cases
   * detect svn 1.8 working copy format to guarantee command line client.
   * <p>
   * For instance, when working copies of several formats are presented in project
   * (though it seems to be rather unlikely case).
   */
  public @NotNull ClientFactory getFactory() {
    return cmdClientFactory;
  }

  @SuppressWarnings("unused")
  public @NotNull ClientFactory getFactory(@NotNull WorkingCopyFormat format) {
    return cmdClientFactory;
  }

  @SuppressWarnings("unused")
  public @NotNull ClientFactory getFactory(@NotNull File file) {
    return cmdClientFactory;
  }

  @SuppressWarnings("unused")
  public @NotNull ClientFactory getFactory(@NotNull File file, boolean useMapping) {
    return cmdClientFactory;
  }

  public @NotNull ClientFactory getFactory(@NotNull Target target) {
    return target.isFile() ? getFactory(target.getFile()) : getFactory();
  }

  public @NotNull ClientFactory getFactoryFromSettings() {
    return cmdClientFactory;
  }

  public @NotNull ClientFactory getCommandLineFactory() {
    return cmdClientFactory;
  }

  public @NotNull WorkingCopyFormat getLowestSupportedFormatForCommandLine() {
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
