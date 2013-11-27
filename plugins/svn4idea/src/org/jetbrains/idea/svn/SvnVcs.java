/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
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
import com.intellij.util.Consumer;
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
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.CmdClientFactory;
import org.jetbrains.idea.svn.api.SvnKitClientFactory;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.commandLine.SvnExecutableChecker;
import org.jetbrains.idea.svn.dialogs.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.LoadedRevisionsCache;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.lowLevel.PrimitivePool;
import org.jetbrains.idea.svn.networking.SSLProtocolExceptionParser;
import org.jetbrains.idea.svn.portable.SvnWcClientI;
import org.jetbrains.idea.svn.properties.PropertyClient;
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
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class SvnVcs extends AbstractVcs<CommittedChangeList> {
  private static final String DO_NOT_LISTEN_TO_WC_DB = "svn.do.not.listen.to.wc.db";
  private static final String KEEP_CONNECTIONS_KEY = "svn.keep.connections";
  private static final Logger REFRESH_LOG = Logger.getInstance("#svn_refresh");
  public static boolean ourListenToWcDb = true;

  private static final int ourLogUsualInterval = 20 * 1000;
  private static final int ourLogRareInterval = 30 * 1000;

  private static final Set<SVNErrorCode> ourLogRarely = new HashSet<SVNErrorCode>(
    Arrays.asList(new SVNErrorCode[]{SVNErrorCode.WC_UNSUPPORTED_FORMAT, SVNErrorCode.WC_CORRUPT, SVNErrorCode.WC_CORRUPT_TEXT_BASE,
      SVNErrorCode.WC_NOT_FILE, SVNErrorCode.WC_NOT_DIRECTORY, SVNErrorCode.WC_PATH_NOT_FOUND}));

  private static final Logger LOG = wrapLogger(Logger.getInstance("org.jetbrains.idea.svn.SvnVcs"));
  @NonNls public static final String VCS_NAME = "svn";
  public static final String VCS_DISPLAY_NAME = "Subversion";

  private static final VcsKey ourKey = createKey(VCS_NAME);
  public static final Topic<Runnable> WC_CONVERTED = new Topic<Runnable>("WC_CONVERTED", Runnable.class);
  private final Map<String, Map<String, Pair<SVNPropertyValue, Trinity<Long, Long, Long>>>> myPropertyCache =
    new SoftHashMap<String, Map<String, Pair<SVNPropertyValue, Trinity<Long, Long, Long>>>>();

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

  //Consumer<Boolean>
  public static final Topic<Consumer> ROOTS_RELOADED = new Topic<Consumer>("ROOTS_RELOADED", Consumer.class);
  private VcsListener myVcsListener;

  private SvnBranchPointsCalculator mySvnBranchPointsCalculator;

  private final RootsToWorkingCopies myRootsToWorkingCopies;
  private final SvnAuthenticationNotifier myAuthNotifier;
  private static RareLogger.LogFilter[] ourLogFilters;
  private final SvnLoadedBrachesStorage myLoadedBranchesStorage;

  public static final String SVNKIT_HTTP_SSL_PROTOCOLS = "svnkit.http.sslProtocols";
  private static boolean ourSSLProtocolsExplicitlySet = false;
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
  private SvnCheckoutProvider myCheckoutProvider;

  @NotNull private final ClientFactory cmdClientFactory;
  @NotNull private final ClientFactory svnKitClientFactory;

  private final boolean myLogExceptions;

  static {
    System.setProperty("svnkit.log.native.calls", "true");
    if (Boolean.getBoolean(DO_NOT_LISTEN_TO_WC_DB)) {
      ourListenToWcDb = false;
    }
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

    ourSSLProtocolsExplicitlySet = System.getProperty(SVNKIT_HTTP_SSL_PROTOCOLS) != null;
  }

  public static boolean isSSLProtocolExplicitlySet() {
    return ourSSLProtocolsExplicitlySet;
  }

  public SvnVcs(final Project project, MessageBus bus, SvnConfiguration svnConfiguration, final SvnLoadedBrachesStorage storage) {
    super(project, VCS_NAME);

    cmdClientFactory = new CmdClientFactory(this);
    svnKitClientFactory = new SvnKitClientFactory(this);

    myLoadedBranchesStorage = storage;
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

    refreshSSLProperty();

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
          invokeRefreshSvnRoots();
        }
      };
    }

    myFrameStateListener = project.isDefault() ? null : new MyFrameStateListener(ChangeListManager.getInstance(project),
                                                                                 VcsDirtyScopeManager.getInstance(project));
    myWorkingCopiesContent = new WorkingCopiesContent(this);

    // remove used some time before old notification group ids
    correctNotificationIds();
    myChecker = new SvnExecutableChecker(myProject);

    Application app = ApplicationManager.getApplication();
    myLogExceptions = app != null && (app.isInternal() || app.isUnitTestMode());
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
    myCopiesRefreshManager = new SvnCopiesRefreshManager((SvnFileUrlMappingImpl) getSvnFileUrlMapping());
    if (! myConfiguration.isCleanupRun()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          cleanup17copies();
          myConfiguration.setCleanupRun(true);
        }
      }, ModalityState.NON_MODAL, myProject.getDisposed());
    } else {
      invokeRefreshSvnRoots();
    }

    myWorkingCopiesContent.activate();
  }

  private void cleanup17copies() {
    final Runnable callCleanupWorker = new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        new CleanupWorker(new VirtualFile[]{}, myProject, "action.Subversion.cleanup.progress.title") {
          @Override
          protected void chanceToFillRoots() {
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
    };

    myCopiesRefreshManager.waitRefresh(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(callCleanupWorker, ModalityState.any());
      }
    });
  }

  public boolean checkCommandLineVersion() {
    boolean isValid = true;

    if (!isProject16() && (myConfiguration.isCommandLine() || isProject18())) {
      isValid = myChecker.checkExecutableAndNotifyIfNeeded();
    }

    return isValid;
  }

  public void invokeRefreshSvnRoots() {
    if (REFRESH_LOG.isDebugEnabled()) {
      REFRESH_LOG.debug("refresh: ", new Throwable());
    }
    if (myCopiesRefreshManager != null) {
      myCopiesRefreshManager.asynchRequest();
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
                                                  "A problem with JNA initialization for SVNKit library. Encryption is not available.",
                                                  NotificationType.WARNING), myProject);
      }
      else if (!SVNJNAUtil.isWinCryptEnabled()) {
        Notifications.Bus.notify(new Notification(getDisplayName(), "Subversion plugin: no encryption",
                                                  "A problem with encryption module (Crypt32.dll) initialization for SVNKit library. " +
                                                  "Encryption is not available.",
                                                  NotificationType.WARNING), myProject);
      }
    }

    final SvnConfiguration.UseAcceleration accelerationType = SvnConfiguration.getInstance(myProject).myUseAcceleration;
    if (SvnConfiguration.UseAcceleration.javaHL.equals(accelerationType)) {
      CheckJavaHL.runtimeCheck(myProject);
    }
    else if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      checkCommandLineVersion();
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

  @NotNull
  private ISVNRepositoryPool getPool() {
    return getPool(myConfiguration.getAuthenticationManager(this));
  }

  @NotNull
  private ISVNRepositoryPool getPool(ISVNAuthenticationManager manager) {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
    return new PrimitivePool(manager, myConfiguration.getOptions(myProject));
  }

  public SVNUpdateClient createUpdateClient() {
    final SVNUpdateClient client = new SVNUpdateClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(this));
    return client;
  }

  public SVNUpdateClient createUpdateClient(@NotNull ISVNAuthenticationManager manager) {
    final SVNUpdateClient client = new SVNUpdateClient(getPool(manager), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(manager);
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

  public SVNWCClient createWCClient(@NotNull ISVNAuthenticationManager manager) {
    final SVNWCClient client = new SVNWCClient(getPool(manager), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(manager);
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

  public SVNLogClient createLogClient(@NotNull ISVNAuthenticationManager manager) {
    final SVNLogClient client = new SVNLogClient(getPool(manager), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(manager);
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
    return VCS_DISPLAY_NAME;
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
  public SVNPropertyValue getPropertyWithCaching(final VirtualFile file, final String propName) throws VcsException {
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

    PropertyClient client = getFactory(ioFile).createPropertyClient();
    final SVNPropertyData value = client.getProperty(SvnTarget.fromFile(ioFile, SVNRevision.WORKING), propName, false, SVNRevision.WORKING);
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
      SVNStatus status = getFactory(file).createStatusClient().doStatus(file, false);
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
      LOG.info(e);
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
  public SVNInfo getInfo(@NotNull SVNURL url,
                         SVNRevision pegRevision,
                         SVNRevision revision) throws SVNException {
    return getFactory().createInfoClient().doInfo(url, pegRevision, revision);
  }

  @Nullable
  public SVNInfo getInfo(@NotNull SVNURL url, SVNRevision revision) throws SVNException {
    return getInfo(url, SVNRevision.UNDEFINED, revision);
  }

  @Nullable
  public SVNInfo getInfo(@NotNull final VirtualFile file) {
    return getInfo(new File(file.getPath()));
  }

  @Nullable
  public SVNInfo getInfo(@NotNull String path) {
    return getInfo(new File(path));
  }

  @Nullable
  public SVNInfo getInfo(@NotNull File ioFile) {
    SVNInfo result = null;
    SvnWcClientI client = getFactory(ioFile).createInfoClient();

    try {
      // applying such behavior only when info requested w/o explicitly specifying revision
      // TODO: logs should be analyzed and decision taken if we need it or just need to explicitly specify HEAD revision in certain calls
      result = client.doInfo(ioFile, SVNRevision.UNDEFINED);
      SVNInfo localInfo = result;
      if (result == null || result.getRepositoryRootURL() == null) {
        LOG.info("Failed to get local info for " + ioFile + ". Trying to get HEAD info.");
        result = client.doInfo(ioFile, SVNRevision.HEAD);
        if (result != null) {
          LOG.info("Local info was " + localInfo + ", HEAD info was " + result);
        }
      }
    }
    catch (SVNException e) {
      handleInfoException(e);
    }

    return result;
  }

  @Nullable
  public SVNInfo getInfo(@NotNull File ioFile, @NotNull SVNRevision revision) {
    SVNInfo result = null;

    try {
      result = getFactory(ioFile).createInfoClient().doInfo(ioFile, revision);
    }
    catch (SVNException e) {
      handleInfoException(e);
    }

    return result;
  }

  private void handleInfoException(SVNException e) {
    final SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();

    if (!myLogExceptions ||
        SvnUtil.isUnversionedOrNotFound(errorCode) ||
        // do not log working copy format vs client version inconsistencies as errors
        SVNErrorCode.WC_UNSUPPORTED_FORMAT.equals(errorCode) ||
        SVNErrorCode.WC_UPGRADE_REQUIRED.equals(errorCode)) {
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

  public void refreshSSLProperty() {
    if (ourSSLProtocolsExplicitlySet) return;
    if (SvnConfiguration.SSLProtocols.all.equals(myConfiguration.SSL_PROTOCOLS)) {
      System.clearProperty(SVNKIT_HTTP_SSL_PROTOCOLS);
    } else if (SvnConfiguration.SSLProtocols.sslv3.equals(myConfiguration.SSL_PROTOCOLS)) {
      System.setProperty(SVNKIT_HTTP_SSL_PROTOCOLS, "SSLv3");
    } else if (SvnConfiguration.SSLProtocols.tlsv1.equals(myConfiguration.SSL_PROTOCOLS)) {
      System.setProperty(SVNKIT_HTTP_SSL_PROTOCOLS, "TLSv1");
    }
  }

  public boolean isWcRoot(FilePath filePath) {
    boolean isWcRoot = false;
    WorkingCopy wcRoot = myRootsToWorkingCopies.getWcRoot(filePath.getVirtualFile());
    if (wcRoot != null) {
      isWcRoot = wcRoot.getFile().getAbsolutePath().equals(filePath.getIOFile().getAbsolutePath());
    }
    return isWcRoot;
  }

  private static class JavaSVNDebugLogger extends SVNDebugLogAdapter {
    private final boolean myLoggingEnabled;
    private final boolean myLogNative;
    private final Logger myLog;
    private final static long ourErrorNotificationInterval = TimeUnit.MINUTES.toMillis(2);
    private long myPreviousTime = 0;

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
      handleSpecificSSLExceptions(th);
      if (shouldLog(logType)) {
        myLog.info(th);
      }
    }

    private void handleSpecificSSLExceptions(Throwable th) {
      final long time = System.currentTimeMillis();
      if ((time - myPreviousTime) <= ourErrorNotificationInterval) {
        return;
      }
      if (th instanceof SSLHandshakeException) {
        // not trusted certificate exception is not the problem, just part of normal behaviour
        if (th.getCause() instanceof SVNSSLUtil.CertificateNotTrustedException) {
          LOG.info(th);
          return;
        }

        myPreviousTime = time;
        String info = SSLExceptionsHelper.getAddInfo();
        info = info == null ? "" : " (" + info + ") ";
        if (th.getCause() instanceof CertificateException) {
          PopupUtil.showBalloonForActiveFrame("Subversion: " + info + th.getCause().getMessage(), MessageType.ERROR);
        } else {
          final String postMessage = "\nPlease check Subversion SSL settings (Settings | Version Control | Subversion | Network)\n" +
                                     "Maybe you should specify SSL protocol manually - SSLv3 or TLSv1";
          PopupUtil.showBalloonForActiveFrame("Subversion: " + info + th.getMessage() + postMessage, MessageType.ERROR);
        }
      } else if (th instanceof SSLProtocolException) {
        final String message = th.getMessage();
        if (! StringUtil.isEmptyOrSpaces(message)) {
          myPreviousTime = time;
          String info = SSLExceptionsHelper.getAddInfo();
          info = info == null ? "" : " (" + info + ") ";
          final SSLProtocolExceptionParser parser = new SSLProtocolExceptionParser(message);
          parser.parse();
          final String errMessage = "Subversion: " + info + parser.getParsedMessage();
          PopupUtil.showBalloonForActiveFrame(errMessage, MessageType.ERROR);
        }
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

      infos.add(new WCInfo(info, SvnUtil.isWorkingCopyRoot(file), SvnUtil.getDepth(this, file)));
    }
    return infos;
  }

  public List<WCInfo> getWcInfosWithErrors() {
    List<WCInfo> result = new ArrayList<WCInfo>(getAllWcInfos());

    for (RootUrlInfo info : getSvnFileUrlMapping().getErrorRoots()) {
      result.add(new WCInfo(info, SvnUtil.isWorkingCopyRoot(info.getIoFile()), SVNDepth.UNKNOWN));
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

  @Override
  public List<AnAction> getAdditionalActionsForLocalChange() {
    return Arrays.<AnAction>asList(new ShowPropertiesDiffWithLocalAction());
  }

  private static String keyForVf(final VirtualFile vf) {
    return vf.getUrl();
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
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
        url = SvnUtil.getUrl(this, ioFile);
        if (url == null) {
          notMatched.add(s);
          continue;
        }
      }
      infos.add(new MyPair<S>(vf, url.toString(), s));
    }
    final List<MyPair<S>> filtered = new UniqueRootsFilter().filter(infos);
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

  @Override
  public CheckoutProvider getCheckoutProvider() {
    if (myCheckoutProvider == null) {
      myCheckoutProvider = new SvnCheckoutProvider();
    }
    return myCheckoutProvider;
  }

  public boolean isProject18() {
    return WorkingCopyFormat.ONE_DOT_EIGHT.equals(getProjectRootFormat());
  }

  public boolean isProject16() {
    return WorkingCopyFormat.ONE_DOT_SIX.equals(getProjectRootFormat());
  }

  private WorkingCopyFormat getProjectRootFormat() {
    return !getProject().isDefault() ? getWorkingCopyFormat(new File(getProject().getBaseDir().getPath())) : WorkingCopyFormat.UNKNOWN;
  }

  /**
   * Detects appropriate client factory based on project root directory working copy format.
   *
   * Try to avoid usages of this method (for now) as it could not correctly for all cases
   * detect svn 1.8 working copy format to guarantee command line client.
   *
   * For instance, when working copies of several formats are presented in project
   * (though it seems to be rather unlikely case).
   *
   * @return
   */
  @NotNull
  public ClientFactory getFactory() {
    return getFactory(getProjectRootFormat(), false);
  }

  public ClientFactory getSvnKitFactory() {
    return svnKitClientFactory;
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
    boolean is18 = WorkingCopyFormat.ONE_DOT_EIGHT.equals(format);
    boolean is16 = WorkingCopyFormat.ONE_DOT_SIX.equals(format);
    boolean isUnknown = WorkingCopyFormat.UNKNOWN.equals(format);

    return is18
           ? cmdClientFactory
           : (is16 ? svnKitClientFactory : (useProjectRootForUnknown && isUnknown ? getFactory() : getFactoryFromSettings()));
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
}
