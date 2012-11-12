/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
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
import com.intellij.util.Processor;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.actions.ShowPropertiesDiffWithLocalAction;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.jetbrains.idea.svn.annotate.SvnAnnotationProvider;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.commandLine.SvnExecutableChecker;
import org.jetbrains.idea.svn.dialogs.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.LoadedRevisionsCache;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.lowLevel.SvnIdeaRepositoryPoolManager;
import org.jetbrains.idea.svn.rollback.SvnRollbackEnvironment;
import org.jetbrains.idea.svn.update.SvnIntegrateEnvironment;
import org.jetbrains.idea.svn.update.SvnUpdateEnvironment;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import javax.swing.*;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.logging.Level;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class SvnVcs extends AbstractVcs<CommittedChangeList> {
  private static final String KEEP_CONNECTIONS_KEY = "svn.keep.connections";
  private static final Logger REFRESH_LOG = Logger.getInstance("#svn_refresh");

  private static final int ourLogUsualInterval = 20 * 1000;
  private static final int ourLogRareInterval = 30 * 1000;

  private static final Set<SVNErrorCode> ourLogRarely = new HashSet<SVNErrorCode>(
    Arrays.asList(new SVNErrorCode[]{SVNErrorCode.WC_UNSUPPORTED_FORMAT, SVNErrorCode.WC_CORRUPT, SVNErrorCode.WC_CORRUPT_TEXT_BASE,
      SVNErrorCode.WC_NOT_FILE, SVNErrorCode.WC_NOT_DIRECTORY, SVNErrorCode.WC_PATH_NOT_FOUND}));

  private static final Logger LOG = wrapLogger(Logger.getInstance("org.jetbrains.idea.svn.SvnVcs"));
  @NonNls public static final String VCS_NAME = "svn";
  private static final VcsKey ourKey = createKey(VCS_NAME);
  public static final Topic<Runnable> WC_CONVERTED = new Topic<Runnable>("WC_CONVERTED", Runnable.class);
  private final Map<String, Map<String, Pair<SVNPropertyValue, Trinity<Long, Long, Long>>>> myPropertyCache =
    new SoftHashMap<String, Map<String, Pair<SVNPropertyValue, Trinity<Long, Long, Long>>>>();

  private SvnIdeaRepositoryPoolManager myPool;
  private final SvnConfiguration myConfiguration;
  private final SvnEntriesFileListener myEntriesFileListener;

  private CheckinEnvironment myCheckinEnvironment;
  private RollbackEnvironment myRollbackEnvironment;
  private UpdateEnvironment mySvnUpdateEnvironment;
  private UpdateEnvironment mySvnIntegrateEnvironment;
  private VcsHistoryProvider mySvnHistoryProvider;
  private AnnotationProvider myAnnotationProvider;
  private DiffProvider mySvnDiffProvider;
  private final VcsShowConfirmationOption myAddConfirmation;
  private final VcsShowConfirmationOption myDeleteConfirmation;
  private EditFileProvider myEditFilesProvider;
  private SvnCommittedChangesProvider myCommittedChangesProvider;
  private final VcsShowSettingOption myCheckoutOptions;

  private ChangeProvider myChangeProvider;
  private MergeProvider myMergeProvider;
  private final WorkingCopiesContent myWorkingCopiesContent;

  @NonNls public static final String LOG_PARAMETER_NAME = "javasvn.log";
  @NonNls public static final String TRACE_NATIVE_CALLS = "javasvn.log.native";
  public static final String pathToEntries = SvnUtil.SVN_ADMIN_DIR_NAME + File.separatorChar + SvnUtil.ENTRIES_FILE_NAME;
  public static final String pathToDirProps = SvnUtil.SVN_ADMIN_DIR_NAME + File.separatorChar + SvnUtil.DIR_PROPS_FILE_NAME;
  private final SvnChangelistListener myChangeListListener;

  private SvnCopiesRefreshManager myCopiesRefreshManager;
  private SvnFileUrlMappingImpl myMapping;
  private final MyFrameStateListener myFrameStateListener;

  public static final Topic<Runnable> ROOTS_RELOADED = new Topic<Runnable>("ROOTS_RELOADED", Runnable.class);
  private VcsListener myVcsListener;

  private SvnBranchPointsCalculator mySvnBranchPointsCalculator;

  private final RootsToWorkingCopies myRootsToWorkingCopies;
  private final SvnAuthenticationNotifier myAuthNotifier;
  private static RareLogger.LogFilter[] ourLogFilters;
  private final SvnLoadedBrachesStorage myLoadedBranchesStorage;

  public static final String SVNKIT_HTTP_SSL_PROTOCOLS = "svnkit.http.sslProtocols";
  private final SvnExecutableChecker myChecker;

  public static final Processor<Exception> ourBusyExceptionProcessor = new Processor<Exception>() {
    @Override
    public boolean process(Exception e) {
      if (e instanceof SVNException) {
        final SVNErrorCode errorCode = ((SVNException)e).getErrorMessage().getErrorCode();
        if (SVNErrorCode.WC_LOCKED.equals(errorCode)) {
          return true;
        } else if (SVNErrorCode.SQLITE_ERROR.equals(errorCode)) {
          Throwable cause = ((SVNException)e).getErrorMessage().getCause();
          if (cause instanceof SqlJetException) {
            return SqlJetErrorCode.BUSY.equals(((SqlJetException)cause).getErrorCode());
          }
        }
      }
      return false;
    }
  };

  public void checkCommandLineVersion() {
    myChecker.checkExecutableAndNotifyIfNeeded();
  }

  static {
    final JavaSVNDebugLogger logger = new JavaSVNDebugLogger(Boolean.getBoolean(LOG_PARAMETER_NAME), Boolean.getBoolean(TRACE_NATIVE_CALLS), LOG);
    SVNDebugLog.setDefaultLog(logger);

    SVNJNAUtil.setJNAEnabled(true);
    SvnHttpAuthMethodsDefaultChecker.check();

    SVNAdminAreaFactory.setSelector(new SvnFormatSelector());

    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
    FSRepositoryFactory.setup();

    // non-optimized writing is fast enough on Linux/MacOS, and somewhat more reliable
    if (SystemInfo.isWindows) {
      SVNAdminArea14.setOptimizedWritingEnabled(true);
    }

    if (!SVNJNAUtil.isJNAPresent()) {
      LOG.warn("JNA is not found by svnkit library");
    }
    initLogFilters();

    // Alexander Kitaev says it is default value (SSLv3) - since 8254
    if (!SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.7") && System.getProperty(SVNKIT_HTTP_SSL_PROTOCOLS) == null) {
      System.setProperty(SVNKIT_HTTP_SSL_PROTOCOLS, "SSLv3");
    }
  }

  public SvnVcs(final Project project, MessageBus bus, SvnConfiguration svnConfiguration, final SvnLoadedBrachesStorage storage) {
    super(project, VCS_NAME);
    myLoadedBranchesStorage = storage;
    LOG.debug("ct");
    myRootsToWorkingCopies = new RootsToWorkingCopies(this);
    myConfiguration = svnConfiguration;
    myAuthNotifier = new SvnAuthenticationNotifier(this);

    dumpFileStatus(FileStatus.ADDED);
    dumpFileStatus(FileStatus.DELETED);
    dumpFileStatus(FileStatus.MERGE);
    dumpFileStatus(FileStatus.MODIFIED);
    dumpFileStatus(FileStatus.NOT_CHANGED);
    dumpFileStatus(FileStatus.UNKNOWN);

    dumpFileStatus(SvnFileStatus.REPLACED);
    dumpFileStatus(SvnFileStatus.EXTERNAL);
    dumpFileStatus(SvnFileStatus.OBSTRUCTED);

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

      myChangeListListener = new SvnChangelistListener(myProject, this);

      myVcsListener = new VcsListener() {
        @Override
        public void directoryMappingChanged() {
          invokeRefreshSvnRoots(true);
        }
      };
    }

    myFrameStateListener = project.isDefault() ? null : new MyFrameStateListener(ChangeListManager.getInstance(project),
                                                                                 VcsDirtyScopeManager.getInstance(project));
    myWorkingCopiesContent = new WorkingCopiesContent(this);

    // remove used some time before old notification group ids
    correctNotificationIds();
    myChecker = new SvnExecutableChecker(myProject);
  }

  private void correctNotificationIds() {
    boolean notEmpty = NotificationsConfigurationImpl.getNotificationsConfigurationImpl().isRegistered("SVN_NO_JNA") ||
                       NotificationsConfigurationImpl.getNotificationsConfigurationImpl().isRegistered("SVN_NO_CRYPT32") ||
                       NotificationsConfigurationImpl.getNotificationsConfigurationImpl().isRegistered("SubversionId");
    if (notEmpty) {
      NotificationsConfigurationImpl.remove("SVN_NO_JNA", "SVN_NO_CRYPT32", "SubversionId");
      NotificationsConfiguration.getNotificationsConfiguration().register(getDisplayName(), NotificationDisplayType.BALLOON);
    }
  }

  public void postStartup() {
    if (myProject.isDefault()) return;
    myCopiesRefreshManager = new SvnCopiesRefreshManager(myProject, (SvnFileUrlMappingImpl) getSvnFileUrlMapping());
    if (! myConfiguration.isCleanupRun()) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          cleanup17copies();
          myConfiguration.setCleanupRun(true);
        }
      });
    } else {
      invokeRefreshSvnRoots(true);
    }

    myWorkingCopiesContent.activate();
  }

  private void cleanup17copies() {
    new CleanupWorker(new VirtualFile[]{}, myProject, "action.Subversion.cleanup.progress.title") {
      @Override
      protected void chanceToFillRoots() {
        myCopiesRefreshManager.getCopiesRefresh().synchRequest();
        final List<WCInfo> infos = getAllWcInfos();
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        final List<VirtualFile> roots = new ArrayList<VirtualFile>(infos.size());
        for (WCInfo info : infos) {
          if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(info.getFormat())) {
            final VirtualFile file = lfs.refreshAndFindFileByIoFile(new File(info.getPath()));
            if (file == null) {
              LOG.info("Wasn't able to find virtual file for wc root: " + info.getPath());
            } else {
              roots.add(file);
            }
          }
        }
        myRoots = roots.toArray(new VirtualFile[roots.size()]);
      }
    }.execute();
  }

  public void invokeRefreshSvnRoots(final boolean asynchronous) {
    REFRESH_LOG.debug("refresh: ", new Throwable());
    if (myCopiesRefreshManager != null) {
      if (asynchronous) {
        myCopiesRefreshManager.getCopiesRefresh().asynchRequest();
      }
      else {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
            @Override
            public void run() {
              myCopiesRefreshManager.getCopiesRefresh().synchRequest();
            }
          }, SvnBundle.message("refreshing.working.copies.roots.progress.text"), true, myProject);
        }
        else {
          myCopiesRefreshManager.getCopiesRefresh().synchRequest();
        }
      }
    }
  }

  @Override
  public boolean checkImmediateParentsBeforeCommit() {
    return true;
  }

  private void upgradeIfNeeded(final MessageBus bus) {
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(ChangeListManagerImpl.LISTS_LOADED, new LocalChangeListsLoadedListener() {
      @Override
      public void processLoadedLists(final List<LocalChangeList> lists) {
        if (lists.isEmpty()) return;
        SvnConfiguration.SvnSupportOptions supportOptions = null;
        try {
          ChangeListManager.getInstance(myProject).setReadOnly(SvnChangeProvider.ourDefaultListName, true);
          supportOptions = myConfiguration.getSupportOptions(myProject);

          if (!supportOptions.changeListsSynchronized()) {
            processChangeLists(lists);
          }
        }
        catch (ProcessCanceledException e) {
          //
        }
        finally {
          if (supportOptions != null) {
            supportOptions.upgrade();
          }
        }

        connection.disconnect();
      }
    });
  }

  public void processChangeLists(final List<LocalChangeList> lists) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstanceChecked(myProject);
    plVcsManager.startBackgroundVcsOperation();
    try {
      final SVNChangelistClient client = createChangelistClient();
      for (LocalChangeList list : lists) {
        if (!list.isDefault()) {
          final Collection<Change> changes = list.getChanges();
          for (Change change : changes) {
            correctListForRevision(plVcsManager, change.getBeforeRevision(), client, list.getName());
            correctListForRevision(plVcsManager, change.getAfterRevision(), client, list.getName());
          }
        }
      }
    }
    finally {
      final Application appManager = ApplicationManager.getApplication();
      if (appManager.isDispatchThread()) {
        appManager.executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            plVcsManager.stopBackgroundVcsOperation();
          }
        });
      }
      else {
        plVcsManager.stopBackgroundVcsOperation();
      }
    }
  }

  private static void correctListForRevision(final ProjectLevelVcsManager plVcsManager, final ContentRevision revision,
                                             final SVNChangelistClient client, final String name) {
    if (revision != null) {
      final FilePath path = revision.getFile();
      final AbstractVcs vcs = plVcsManager.getVcsFor(path);
      if (vcs != null && VCS_NAME.equals(vcs.getName())) {
        try {
          client.doAddToChangelist(new File[]{path.getIOFile()}, SVNDepth.EMPTY, name, null);
        }
        catch (SVNException e) {
          // left in default list
        }
      }
    }
  }

  @Override
  public void activate() {
    createPool();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (!myProject.isDefault()) {
      ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);
      vcsManager.addVcsListener(myVcsListener);
    }

    SvnApplicationSettings.getInstance().svnActivated();
    if (myEntriesFileListener != null) {
      VirtualFileManager.getInstance().addVirtualFileListener(myEntriesFileListener);
    }
    // this will initialize its inner listener for committed changes upload
    LoadedRevisionsCache.getInstance(myProject);
    FrameStateManager.getInstance().addListener(myFrameStateListener);

    myAuthNotifier.init();
    mySvnBranchPointsCalculator = new SvnBranchPointsCalculator(myProject);
    mySvnBranchPointsCalculator.activate();

    if (SystemInfo.isWindows) {
      if (!SVNJNAUtil.isJNAPresent()) {
        Notifications.Bus.notify(new Notification(getDisplayName(), "Subversion plugin: no JNA",
                                                  "A problem with JNA initialization for svnkit library. Encryption is not available.",
                                                  NotificationType.WARNING),
                                 NotificationDisplayType.BALLOON, myProject);
      }
      else if (!SVNJNAUtil.isWinCryptEnabled()) {
        Notifications.Bus.notify(new Notification(getDisplayName(), "Subversion plugin: no encryption",
                                                  "A problem with encryption module (Crypt32.dll) initialization for svnkit library. Encryption is not available.",
                                                  NotificationType.WARNING), NotificationDisplayType.BALLOON, myProject);
      }
    }

    final SvnConfiguration.UseAcceleration accelerationType = SvnConfiguration.getInstance(myProject).myUseAcceleration;
    if (SvnConfiguration.UseAcceleration.javaHL.equals(accelerationType)) {
      CheckJavaHL.runtimeCheck(myProject);
    }
    else if (SvnConfiguration.UseAcceleration.commandLine.equals(accelerationType) &&
             !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myChecker.checkExecutableAndNotifyIfNeeded();
    }

    // do one time after project loaded
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
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
      }
    });

    vcsManager.addVcsListener(myRootsToWorkingCopies);

    myLoadedBranchesStorage.activate();
  }

  private static void initLogFilters() {
    if (ourLogFilters != null) return;
    ourLogFilters = new RareLogger.LogFilter[]{new RareLogger.LogFilter() {
      @Override
      public Object getKey(@NotNull org.apache.log4j.Level level,
                           @NonNls String message,
                           @Nullable Throwable t,
                           @NonNls String... details) {
        SVNException svnExc = null;
        if (t instanceof SVNException) {
          svnExc = (SVNException)t;
        }
        else if (t instanceof VcsException && t.getCause() instanceof SVNException) {
          svnExc = (SVNException)t.getCause();
        }
        if (svnExc != null) {
          // only filter a few cases
          if (ourLogRarely.contains(svnExc.getErrorMessage().getErrorCode())) {
            return svnExc.getErrorMessage().getErrorCode();
          }
        }
        return null;
      }

      @Override
      @NotNull
      public Integer getAllowedLoggingInterval(org.apache.log4j.Level level, String message, Throwable t, String[] details) {
        SVNException svnExc = null;
        if (t instanceof SVNException) {
          svnExc = (SVNException)t;
        }
        else if (t instanceof VcsException && t.getCause() instanceof SVNException) {
          svnExc = (SVNException)t.getCause();
        }
        if (svnExc != null) {
          if (ourLogRarely.contains(svnExc.getErrorMessage().getErrorCode())) {
            return ourLogRareInterval;
          }
          else {
            return ourLogUsualInterval;
          }
        }
        return 0;
      }
    }};
  }

  public static Logger wrapLogger(final Logger logger) {
    initLogFilters();
    return RareLogger.wrap(logger, Boolean.getBoolean("svn.logger.fairsynch"), ourLogFilters);
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

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (myVcsListener != null) {
      vcsManager.removeVcsListener(myVcsListener);
    }

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
    vcsManager.removeVcsListener(myRootsToWorkingCopies);
    myRootsToWorkingCopies.clear();

    myAuthNotifier.stop();
    myAuthNotifier.clear();

    mySvnBranchPointsCalculator.deactivate();
    mySvnBranchPointsCalculator = null;
    myWorkingCopiesContent.deactivate();
    myLoadedBranchesStorage.deactivate();
    myPool.dispose();
    myPool = null;
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

  public SVNRepository createRepository(String url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    repos.setTunnelProvider(myConfiguration.getOptions(myProject));
    return repos;
  }

  public SVNRepository createRepository(SVNURL url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(url);
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    repos.setTunnelProvider(myConfiguration.getOptions(myProject));
    return repos;
  }

  private void createPool() {
    if (myPool != null) return;
    final String property = System.getProperty(KEEP_CONNECTIONS_KEY);
    final boolean keep;
    boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    // pool variant by default
    if (StringUtil.isEmptyOrSpaces(property) || unitTestMode) {
      keep = ! unitTestMode;  // default
    } else {
      keep = Boolean.getBoolean(KEEP_CONNECTIONS_KEY);
    }
    myPool = new SvnIdeaRepositoryPoolManager(false, myConfiguration.getAuthenticationManager(this), myConfiguration.getOptions(myProject));
  }

  @NotNull
  private ISVNRepositoryPool getPool() {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
    if (myPool == null) {
      createPool();
    }
    return myPool;
  }

  public SVNUpdateClient createUpdateClient() {
    final SVNUpdateClient client = new SVNUpdateClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNStatusClient createStatusClient() {
    SVNStatusClient client = new SVNStatusClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    client.setIgnoreExternals(false);
    return client;
  }

  public SVNWCClient createWCClient() {
    final SVNWCClient client = new SVNWCClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNCopyClient createCopyClient() {
    final SVNCopyClient client = new SVNCopyClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNMoveClient createMoveClient() {
    final SVNMoveClient client = new SVNMoveClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNLogClient createLogClient() {
    final SVNLogClient client = new SVNLogClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNCommitClient createCommitClient() {
    final SVNCommitClient client = new SVNCommitClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNDiffClient createDiffClient() {
    final SVNDiffClient client = new SVNDiffClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNChangelistClient createChangelistClient() {
    final SVNChangelistClient client = new SVNChangelistClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNWCAccess createWCAccess() {
    final SVNWCAccess access = SVNWCAccess.newInstance(null);
    access.setOptions(myConfiguration.getOptions(myProject));
    return access;
  }

  public ISVNOptions getSvnOptions() {
    return myConfiguration.getOptions(myProject);
  }

  public ISVNAuthenticationManager getSvnAuthenticationManager() {
    return myConfiguration.getAuthenticationManager(this);
  }

  void dumpFileStatus(FileStatus fs) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("FileStatus:" + fs.getText() + " " + fs.getColor() + " " + " " + fs.getClass().getName());
    }
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
    LOG.debug("getDisplayName");
    return "Subversion";
  }

  @Override
  public Configurable getConfigurable() {
    LOG.debug("createConfigurable");
    return new SvnConfigurable(myProject);
  }


  public SvnConfiguration getSvnConfiguration() {
    return myConfiguration;
  }

  public static SvnVcs getInstance(Project project) {
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
    return myAnnotationProvider;
  }

  public void addEntriesListener(final SvnEntriesListener listener) {
    if (myEntriesFileListener != null) {
      myEntriesFileListener.addListener(listener);
    }
  }

  public void removeEntriesListener(final SvnEntriesListener listener) {
    if (myEntriesFileListener != null) {
      myEntriesFileListener.removeListener(listener);
    }
  }

  public SvnEntriesFileListener getEntriesFileListener() {
    return myEntriesFileListener;
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
    return new Trinity<Long, Long, Long>(new File(dir, relPath).lastModified(), new File(dir, relPathBase).lastModified(),
                                         new File(dir, relPathRevert).lastModified());
  }

  private static boolean trinitiesEqual(final Trinity<Long, Long, Long> t1, final Trinity<Long, Long, Long> t2) {
    if (t2.first == 0 && t2.second == 0 && t2.third == 0) return false;
    return t1.equals(t2);
  }

  @Nullable
  public SVNPropertyValue getPropertyWithCaching(final VirtualFile file, final String propName) throws SVNException {
    Map<String, Pair<SVNPropertyValue, Trinity<Long, Long, Long>>> cachedMap = myPropertyCache.get(keyForVf(file));
    final Pair<SVNPropertyValue, Trinity<Long, Long, Long>> cachedValue = cachedMap == null ? null : cachedMap.get(propName);

    final File ioFile = new File(file.getPath());
    final Trinity<Long, Long, Long> tsTrinity = getTimestampForPropertiesChange(ioFile, file.isDirectory());

    if (cachedValue != null) {
      // zero means that a file was not found
      if (trinitiesEqual(cachedValue.getSecond(), tsTrinity)) {
        return cachedValue.getFirst();
      }
    }

    final SVNPropertyData value = createWCClient().doGetProperty(ioFile, propName, SVNRevision.WORKING, SVNRevision.WORKING);
    final SVNPropertyValue propValue = value == null ? null : value.getValue();

    if (cachedMap == null) {
      cachedMap = new HashMap<String, Pair<SVNPropertyValue, Trinity<Long, Long, Long>>>();
      myPropertyCache.put(keyForVf(file), cachedMap);
    }

    cachedMap.put(propName, new Pair<SVNPropertyValue, Trinity<Long, Long, Long>>(propValue, tsTrinity));

    return propValue;
  }

  @Override
  public boolean fileExistsInVcs(FilePath path) {
    File file = path.getIOFile();
    try {
      SVNStatus status = createStatusClient().doStatus(file, false);
      if (status != null) {
        if (svnStatusIs(status, SVNStatusType.STATUS_ADDED)) {
          return status.isCopied();
        }
        return !(svnStatusIsUnversioned(status) ||
                 svnStatusIs(status, SVNStatusType.STATUS_IGNORED) ||
                 svnStatusIs(status, SVNStatusType.STATUS_OBSTRUCTED));
      }
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  public static boolean svnStatusIsUnversioned(final SVNStatus status) {
    return svnStatusIs(status, SVNStatusType.STATUS_UNVERSIONED);
  }

  public static boolean svnStatusIs(final SVNStatus status, @NotNull final SVNStatusType value) {
    return value.equals(status.getNodeStatus()) || value.equals(status.getContentsStatus());
  }

  @Override
  public boolean fileIsUnderVcs(FilePath path) {
    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
    final VirtualFile file = path.getVirtualFile();
    if (file == null) {
      return false;
    }
    return !SvnStatusUtil.isIgnoredInAnySense(clManager, file) && !clManager.isUnversioned(file);
  }

  private static File getEntriesFile(File file) {
    return file.isDirectory() ? new File(file, pathToEntries) : new File(file.getParentFile(), pathToEntries);
  }

  private static File getDirPropsFile(File file) {
    return new File(file, pathToDirProps);
  }

  @Nullable
  public SVNInfo getInfo(final VirtualFile file) {
    final File ioFile = new File(file.getPath());
    return getInfo(ioFile);
  }

  public SVNInfo getInfo(File ioFile) {
    try {
      SVNWCClient wcClient = createWCClient();
      SVNInfo info = wcClient.doInfo(ioFile, SVNRevision.UNDEFINED);
      if (info == null || info.getRepositoryRootURL() == null) {
        info = wcClient.doInfo(ioFile, SVNRevision.HEAD);
      }
      return info;
    }
    catch (SVNException e) {
      return null;
    }
  }

  private static class JavaSVNDebugLogger extends SVNDebugLogAdapter {
    private final boolean myLoggingEnabled;
    private final boolean myLogNative;
    private final Logger myLog;

    public JavaSVNDebugLogger(boolean loggingEnabled, boolean logNative, Logger log) {
      myLoggingEnabled = loggingEnabled;
      myLogNative = logNative;
      myLog = log;
    }

    private boolean shouldLog(final SVNLogType logType) {
      return myLoggingEnabled || myLogNative && SVNLogType.NATIVE_CALL.equals(logType);
    }

    @Override
    public void log(final SVNLogType logType, final Throwable th, final Level logLevel) {
      if (shouldLog(logType)) {
        myLog.info(th);
      }
    }

    @Override
    public void log(final SVNLogType logType, final String message, final Level logLevel) {
      if (SVNLogType.NATIVE_CALL.equals(logType)) {
        logNative(message);
      }
      if (shouldLog(logType)) {
        myLog.info(message);
      }
    }

    private static void logNative(String message) {
      if (message == null) return;
      final NativeLogReader.CallInfo callInfo = SvnNativeLogParser.parse(message);
      if (callInfo == null) return;
      NativeLogReader.putInfo(callInfo);
    }

    @Override
    public void log(final SVNLogType logType, final String message, final byte[] data) {
      if (shouldLog(logType)) {
        if (data != null) {
          try {
            myLog.info(message + "\n" + new String(data, "UTF-8"));
          }
          catch (UnsupportedEncodingException e) {
            myLog.info(message + "\n" + new String(data));
          }
        }
        else {
          myLog.info(message);
        }
      }
    }
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
      myCommittedChangesProvider = new SvnCommittedChangesProvider(myProject);
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
    final List<WCInfo> infos = new ArrayList<WCInfo>();
    for (RootUrlInfo info : infoList) {
      final File file = info.getIoFile();
      infos.add(new WCInfo(file.getAbsolutePath(), info.getAbsoluteUrlAsUrl(),
                           info.getFormat(), info.getRepositoryUrl(), SvnUtil.isWorkingCopyRoot(file), info.getType(),
                           SvnUtil.getDepth(this, file)));
    }
    return infos;
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

  @Override
  public List<AnAction> getAdditionalActionsForLocalChange() {
    return Arrays.<AnAction>asList(new ShowPropertiesDiffWithLocalAction());
  }

  private static String keyForVf(final VirtualFile vf) {
    return vf.getUrl();
  }

  @Override
  public boolean allowsNestedRoots() {
    return SvnConfiguration.getInstance(myProject).DETECT_NESTED_COPIES;
  }

  @Override
  public <S> List<S> filterUniqueRoots(final List<S> in, final Convertor<S, VirtualFile> convertor) {
    if (in.size() <= 1) return in;

    final List<MyPair<S>> infos = new ArrayList<MyPair<S>>(in.size());
    final SvnFileUrlMappingImpl mapping = (SvnFileUrlMappingImpl)getSvnFileUrlMapping();
    final List<S> notMatched = new LinkedList<S>();
    for (S s : in) {
      final VirtualFile vf = convertor.convert(s);
      if (vf == null) continue;

      final File ioFile = new File(vf.getPath());
      SVNURL url = mapping.getUrlForFile(ioFile);
      if (url == null) {
        url = SvnUtil.getUrl(ioFile);
        if (url == null) {
          notMatched.add(s);
          continue;
        }
      }
      infos.add(new MyPair<S>(vf, url.toString(), s));
    }
    final List<MyPair<S>> filtered = new ArrayList<MyPair<S>>(infos.size());
    ForNestedRootChecker.filterOutSuperfluousChildren(this, infos, filtered);

    final List<S> converted = ObjectsConvertor.convert(filtered, new Convertor<MyPair<S>, S>() {
      @Override
      public S convert(final MyPair<S> o) {
        return o.getSrc();
      }
    });
    if (!notMatched.isEmpty()) {
      // potential bug is here: order is not kept. but seems it only occurs for cases where result is sorted after filtering so ok
      converted.addAll(notMatched);
    }
    return converted;
  }

  private static class MyPair<T> implements RootUrlPair {
    private final VirtualFile myFile;
    private final String myUrl;
    private final T mySrc;

    private MyPair(VirtualFile file, String url, T src) {
      myFile = file;
      myUrl = url;
      mySrc = src;
    }

    public T getSrc() {
      return mySrc;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return myFile;
    }

    @Override
    public String getUrl() {
      return myUrl;
    }
  }

  private static class MyFrameStateListener implements FrameStateListener {
    private final ChangeListManager myClManager;
    private final VcsDirtyScopeManager myDirtyScopeManager;

    private MyFrameStateListener(ChangeListManager clManager, VcsDirtyScopeManager dirtyScopeManager) {
      myClManager = clManager;
      myDirtyScopeManager = dirtyScopeManager;
    }

    @Override
    public void onFrameDeactivated() {
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
  public boolean isVcsBackgroundOperationsAllowed(VirtualFile root) {
    return ThreeState.YES.equals(myAuthNotifier.isAuthenticatedFor(root));
  }

  public SvnBranchPointsCalculator getSvnBranchPointsCalculator() {
    return mySvnBranchPointsCalculator;
  }

  @Override
  public boolean areDirectoriesVersionedItems() {
    return true;
  }
}
