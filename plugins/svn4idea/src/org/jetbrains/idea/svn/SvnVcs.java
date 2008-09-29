/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.ShowPropertiesDiffWithLocalAction;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.jetbrains.idea.svn.annotate.SvnAnnotationProvider;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.dialogs.SvnFormatWorker;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.LoadedRevisionsCache;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.rollback.SvnRollbackEnvironment;
import org.jetbrains.idea.svn.update.SvnIntegrateEnvironment;
import org.jetbrains.idea.svn.update.SvnUpdateEnvironment;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.logging.Level;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class SvnVcs extends AbstractVcs {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnVcs");
  private final Map<VirtualFile, SVNStatusHolder> myStatuses = new SoftHashMap<VirtualFile, SVNStatusHolder>();
  private final Map<VirtualFile, SVNInfoHolder> myInfos = new SoftHashMap<VirtualFile, SVNInfoHolder>();
  private final Map<VirtualFile, Map<String, Pair<SVNPropertyValue, Long>>> myPropertyCache = new SoftHashMap<VirtualFile, Map<String, Pair<SVNPropertyValue, Long>>>();

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

  private final SvnFileUrlMappingRefresher myRootsInfo;

  private ChangeProvider myChangeProvider;
  private MergeProvider myMergeProvider;

  @NonNls public static final String LOG_PARAMETER_NAME = "javasvn.log";
  @NonNls public static final String VCS_NAME = "svn";
  public static final String pathToEntries = SvnUtil.SVN_ADMIN_DIR_NAME + File.separatorChar + SvnUtil.ENTRIES_FILE_NAME;
  public static final String pathToDirProps = SvnUtil.SVN_ADMIN_DIR_NAME + File.separatorChar + SvnUtil.DIR_PROPS_FILE_NAME;
  private final SvnChangelistListener myChangeListListener;

  static {
    //noinspection UseOfArchaicSystemPropertyAccessors
    final JavaSVNDebugLogger logger = new JavaSVNDebugLogger(Boolean.getBoolean(LOG_PARAMETER_NAME), LOG);
    SVNDebugLog.setDefaultLog(logger);
    SVNAdminAreaFactory.setSelector(new SvnFormatSelector());

    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
    FSRepositoryFactory.setup();

    // non-optimized writing is fast enough on Linux/MacOS, and somewhat more reliable
    if (SystemInfo.isWindows) {
      SVNAdminArea14.setOptimizedWritingEnabled(true);
    }
  }

  private static Boolean booleanProperty(final String systemParameterName) {
    return Boolean.valueOf(System.getProperty(systemParameterName));
  }

  public SvnVcs(Project project, SvnConfiguration svnConfiguration) {
    super(project);
    LOG.debug("ct");
    myConfiguration = svnConfiguration;

    dumpFileStatus(FileStatus.ADDED);
    dumpFileStatus(FileStatus.DELETED);
    dumpFileStatus(FileStatus.MERGE);
    dumpFileStatus(FileStatus.MODIFIED);
    dumpFileStatus(FileStatus.NOT_CHANGED);
    dumpFileStatus(FileStatus.UNKNOWN);

    dumpFileStatus(SvnFileStatus.REPLACED);
    dumpFileStatus(SvnFileStatus.EXTERNAL);
    dumpFileStatus(SvnFileStatus.OBSTRUCTED);

    myEntriesFileListener = new SvnEntriesFileListener(project);

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    myAddConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    myDeleteConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);
    myCheckoutOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);

    myRootsInfo = new SvnFileUrlMappingRefresher(new SvnFileUrlMappingImpl(myProject, this));

    final SvnBranchConfigurationManager.SvnSupportOptions supportOptions =
      SvnBranchConfigurationManager.getInstance(myProject).getSupportOptions();
    upgradeTo15(supportOptions);
    changeListSynchronizationIdeaVersionToNative(supportOptions);

    if (myProject.isDefault()) {
      myChangeListListener = null;
    } else {
      myChangeListListener = new SvnChangelistListener(myProject, createChangelistClient());
      ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);
    }
  }

  private void changeListSynchronizationIdeaVersionToNative(final SvnBranchConfigurationManager.SvnSupportOptions supportOptions) {
      final MessageBusConnection messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
      messageBusConnection.subscribe(ChangeListManagerImpl.LISTS_LOADED, new LocalChangeListsLoadedListener() {
        public void processLoadedLists(final List<LocalChangeList> lists) {
          ChangeListManager.getInstanceChecked(myProject).setReadOnly(SvnChangeProvider.ourDefaultListName, true);

          if (! supportOptions.changeListsSynchronized()) {
            try {
              processChangeLists(lists);
              supportOptions.upgradeToChangeListsSynchronized();
            }
            catch (ProcessCanceledException e) {
              //
            }
          }
          messageBusConnection.disconnect();
        }
      });
  }

  public void processChangeLists(final List<LocalChangeList> lists) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstanceChecked(myProject);
    plVcsManager.startBackgroundVcsOperation();
    try {
      final SVNChangelistClient client = createChangelistClient();
      for (LocalChangeList list : lists) {
        if (! list.isDefault()) {
          final Collection<Change> changes = list.getChanges();
          for (Change change : changes) {
            correctListForRevision(plVcsManager, change.getBeforeRevision(), client, list.getName());
            correctListForRevision(plVcsManager, change.getAfterRevision(), client, list.getName());
          }
        }
      }
    }
    finally {
      plVcsManager.stopBackgroundVcsOperation();
    }
  }

  private void correctListForRevision(final ProjectLevelVcsManager plVcsManager, final ContentRevision revision,
                                      final SVNChangelistClient client, final String name) {
    if (revision != null) {
      final FilePath path = revision.getFile();
      final AbstractVcs vcs = plVcsManager.getVcsFor(path);
      if ((vcs != null) && VCS_NAME.equals(vcs.getName())) {
        try {
          client.doAddToChangelist(new File[] {path.getIOFile()}, SVNDepth.EMPTY, name, null);
        }
        catch (SVNException e) {
          // left in default list
        }
      }
    }
  }

  private void upgradeTo15(final SvnBranchConfigurationManager.SvnSupportOptions supportOptions) {
    if (! supportOptions.upgradeTo15Asked()) {
      final SvnWorkingCopyChecker workingCopyChecker = new SvnWorkingCopyChecker();

      if (workingCopyChecker.upgradeNeeded()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            // ask for upgrade
            final int upgradeAnswer = Messages.showYesNoDialog(SvnBundle.message("upgrade.format.to15.question.text",
              SvnBundle.message("label.where.svn.format.can.be.changed.text", SvnBundle.message("action.show.svn.map.text"))),
              SvnBundle.message("upgrade.format.to15.question.title"), Messages.getWarningIcon());
            if (DialogWrapper.OK_EXIT_CODE == upgradeAnswer) {
              ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                public void run() {
                  workingCopyChecker.doUpgrade();
                }
              });
            }
          }
        });
      }
    }
  }

  @Override
  public void activate() {
    super.activate();
    SvnApplicationSettings.getInstance().svnActivated();
    VirtualFileManager.getInstance().addVirtualFileListener(myEntriesFileListener);
    // this will initialize its inner listener for committed changes upload
    LoadedRevisionsCache.getInstance(myProject);
  }

  @Override
  public void deactivate() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myEntriesFileListener);
    SvnApplicationSettings.getInstance().svnDeactivated();
    new DefaultSVNRepositoryPool(null, null).shutdownConnections(true);
    if (myCommittedChangesProvider != null) {
      myCommittedChangesProvider.deactivate();
    }
    if (myChangeListListener != null && (! myProject.isDefault())) {
      ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListListener);
    }
    super.deactivate();
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

  public EditFileProvider getEditFileProvider() {
    if (myEditFilesProvider == null) {
      myEditFilesProvider = new SvnEditFileProvider(this);
    }
    return myEditFilesProvider;
  }

  @NotNull
  public ChangeProvider getChangeProvider() {
    if (myChangeProvider == null) {
      myChangeProvider = new SvnChangeProvider(this);
    }
    return myChangeProvider;
  }

  public SVNRepository createRepository(String url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(myProject));
    return repos;
  }

  public SVNRepository createRepository(SVNURL url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(url);
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(myProject));
    return repos;
  }

  public SVNUpdateClient createUpdateClient() {
    return new SVNUpdateClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNStatusClient createStatusClient() {
    return new SVNStatusClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNWCClient createWCClient() {
    return new SVNWCClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNCopyClient createCopyClient() {
    return new SVNCopyClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNMoveClient createMoveClient() {
    return new SVNMoveClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNLogClient createLogClient() {
    return new SVNLogClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNCommitClient createCommitClient() {
    return new SVNCommitClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNDiffClient createDiffClient() {
    return new SVNDiffClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNChangelistClient createChangelistClient() {
    return new SVNChangelistClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
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
    return myConfiguration.getAuthenticationManager(myProject);
  }

  void dumpFileStatus(FileStatus fs) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("FileStatus:" + fs.getText() + " " + fs.getColor() + " " + " " + fs.getClass().getName());
    }
  }

  public UpdateEnvironment getIntegrateEnvironment() {
    if (mySvnIntegrateEnvironment == null) {
      mySvnIntegrateEnvironment = new SvnIntegrateEnvironment(this);
    }
    return mySvnIntegrateEnvironment;
  }

  public UpdateEnvironment getUpdateEnvironment() {
    if (mySvnUpdateEnvironment == null) {
      mySvnUpdateEnvironment = new SvnUpdateEnvironment(this);
    }
    return mySvnUpdateEnvironment;
  }

  public String getName() {
    LOG.debug("getName");
    return VCS_NAME;
  }

  public String getDisplayName() {
    LOG.debug("getDisplayName");
    return "Subversion";
  }

  public Configurable getConfigurable() {
    LOG.debug("createConfigurable");
    return new SvnConfigurable(myProject);
  }

  public Project getProject() {
    return myProject;
  }

  public SvnConfiguration getSvnConfiguration() {
    return myConfiguration;
  }

  public static SvnVcs getInstance(Project project) {
    return (SvnVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME);
  }

  @NotNull
  public CheckinEnvironment getCheckinEnvironment() {
    if (myCheckinEnvironment == null) {
      myCheckinEnvironment = new SvnCheckinEnvironment(this);
    }
    return myCheckinEnvironment;
  }

  @NotNull
  public RollbackEnvironment getRollbackEnvironment() {
    if (myRollbackEnvironment == null) {
      myRollbackEnvironment = new SvnRollbackEnvironment(this);
    }
    return myRollbackEnvironment;
  }

  public VcsHistoryProvider getVcsHistoryProvider() {
    // no heavy state, but it would be useful to have place to keep state in -> do not reuse instance
    return new SvnHistoryProvider(this);
  }

  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return getVcsHistoryProvider();
  }

  public AnnotationProvider getAnnotationProvider() {
    if (myAnnotationProvider == null) {
      myAnnotationProvider = new SvnAnnotationProvider(this);
    }
    return myAnnotationProvider;
  }

  public SvnEntriesFileListener getSvnEntriesFileListener() {
    return myEntriesFileListener;
  }

  public DiffProvider getDiffProvider() {
    if (mySvnDiffProvider == null) {
      mySvnDiffProvider = new SvnDiffProvider(this);
    }
    return mySvnDiffProvider;
  }

  @Nullable
  public SVNStatusHolder getCachedStatus(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    SVNStatusHolder value = myStatuses.get(vFile);
    File file = new File(vFile.getPath());
    File entriesFile = getEntriesFile(file);
    File lockFile = new File(entriesFile.getParentFile(), SvnUtil.LOCK_FILE_NAME);
    // value 0 for modified time also returned for not existing file
    if (value != null && (value.getEntriesTimestamp() == entriesFile.lastModified()) && (value.getEntriesTimestamp() != 0) &&
        value.getFileTimestamp() == vFile.getTimeStamp() && value.isLocked() == lockFile.exists()) {
      return value;
    }
    return null;
  }

  public void cacheStatus(VirtualFile vFile, SVNStatus status) {
    if (vFile == null) {
      return;
    }
    File file = new File(vFile.getPath());
    File entriesFile = getEntriesFile(file);
    myStatuses.put(vFile, new SVNStatusHolder(entriesFile.lastModified(), vFile.getTimeStamp(), status));
  }

  @Nullable
  public SVNInfoHolder getCachedInfo(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }

    SVNInfoHolder value = myInfos.get(vFile);
    File file = new File(vFile.getPath());
    File entriesFile = getEntriesFile(file);
    if (value != null && value.getEntriesTimestamp() == entriesFile.lastModified() &&
        value.getFileTimestamp() == vFile.getTimeStamp()) {
      return value;
    }
    return null;
  }

  public void cacheInfo(VirtualFile vFile, SVNInfo info) {
    if (vFile == null) {
      return;
    }
    File file = new File(vFile.getPath());
    File entriesFile = getEntriesFile(file);
    myInfos.put(vFile, new SVNInfoHolder(entriesFile.lastModified(), vFile.getTimeStamp(), info));
  }

  @Nullable
  public SVNPropertyValue getPropertyWithCaching(final VirtualFile file, final String propName) throws SVNException {
    Map<String, Pair<SVNPropertyValue, Long>> cachedMap = myPropertyCache.get(file);
    final Pair<SVNPropertyValue, Long> cachedValue = (cachedMap == null) ? null : cachedMap.get(propName);

    final File ioFile = new File(file.getPath());
    final File dirPropsFile = getDirPropsFile(ioFile);
    long dirPropsModified = dirPropsFile.lastModified();

    if (cachedValue != null) {
      if (dirPropsModified == cachedValue.getSecond().longValue()) {
        return cachedValue.getFirst();
      }
    }

    final SVNPropertyData value = createWCClient().doGetProperty(ioFile, propName, SVNRevision.WORKING, SVNRevision.WORKING);
    final SVNPropertyValue propValue = (value == null) ? null : value.getValue();

    if (cachedMap == null) {
      cachedMap = new HashMap<String, Pair<SVNPropertyValue, Long>>();
      myPropertyCache.put(file, cachedMap);
    }

    cachedMap.put(propName, new Pair<SVNPropertyValue, Long>(propValue, dirPropsModified));

    return propValue;
  }

  @Nullable
  public SVNStatus getStatusWithCaching(final VirtualFile file) {
    SVNStatusHolder statusHolder = getCachedStatus(file);
    if (statusHolder != null) {
      return statusHolder.getStatus();
    }
    try {
      final SVNStatus status = createStatusClient().doStatus(new File(file.getPath()), false);
      cacheStatus(file, status);
      return status;
    }
    catch (SVNException e) {
      cacheStatus(file, null);
    }
    return null;
  }

  public boolean fileExistsInVcs(FilePath path) {
    File file = path.getIOFile();
    SVNStatus status;
    try {
      SVNStatusHolder statusValue = getCachedStatus(path.getVirtualFile());
      if (statusValue != null) {
        status = statusValue.getStatus();
      }
      else {
        status = createStatusClient().doStatus(file, false);
        cacheStatus(path.getVirtualFile(), status);
      }
      if (status != null) {
        final SVNStatusType statusType = status.getContentsStatus();
        if (statusType == SVNStatusType.STATUS_ADDED) {
          return status.isCopied();
        }
        return !(status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                 status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
                 status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED);
      }
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  public boolean fileIsUnderVcs(FilePath path) {
    File file = path.getIOFile();
    SVNStatus status;
    try {
      SVNStatusHolder statusValue = getCachedStatus(path.getVirtualFile());
      if (statusValue != null) {
        status = statusValue.getStatus();
      }
      else {
        status = createStatusClient().doStatus(file, false);
        cacheStatus(path.getVirtualFile(), status);
      }
      return status != null && !(status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED);
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  private static File getEntriesFile(File file) {
    return file.isDirectory() ? new File(file, pathToEntries) : new File(file.getParentFile(), pathToEntries);
  }

  private static File getDirPropsFile(File file) {
    return new File(file, pathToDirProps);
  }

  @Nullable
  public SVNInfo getInfoWithCaching(final VirtualFile file) {
    SVNInfo info;
    SVNInfoHolder infoValue = getCachedInfo(file);
    if (infoValue != null) {
      info = infoValue.getInfo();
    } else {
      try {
        SVNWCClient wcClient = new SVNWCClient(getSvnAuthenticationManager(), getSvnOptions());
        info = wcClient.doInfo(new File(file.getPath()), SVNRevision.WORKING);
      }
      catch (SVNException e) {
        info = null;
      }
      cacheInfo(file, info);
    }
    return info;
  }

  public static class SVNStatusHolder {

    private SVNStatus myValue;
    private long myEntriesTimestamp;
    private long myFileTimestamp;
    private boolean myIsLocked;

    public SVNStatusHolder(long entriesStamp, long fileStamp, SVNStatus value) {
      myValue = value;
      myEntriesTimestamp = entriesStamp;
      myFileTimestamp = fileStamp;
      myIsLocked = value != null && value.isLocked();
    }

    public long getEntriesTimestamp() {
      return myEntriesTimestamp;
    }

    public long getFileTimestamp() {
      return myFileTimestamp;
    }

    public boolean isLocked() {
      return myIsLocked;
    }

    public SVNStatus getStatus() {
      return myValue;
    }
  }

  public static class SVNInfoHolder {

    private SVNInfo myValue;
    private long myEntriesTimestamp;
    private long myFileTimestamp;

    public SVNInfoHolder(long entriesStamp, long fileStamp, SVNInfo value) {
      myValue = value;
      myEntriesTimestamp = entriesStamp;
      myFileTimestamp = fileStamp;
    }

    public long getEntriesTimestamp() {
      return myEntriesTimestamp;
    }

    public long getFileTimestamp() {
      return myFileTimestamp;
    }

    public SVNInfo getInfo() {
      return myValue;
    }
  }

  private static class JavaSVNDebugLogger extends SVNDebugLogAdapter {
    private final boolean myLoggingEnabled;
    private final Logger myLog;
    @NonNls public static final String TRACE_LOG_PARAMETER_NAME = "javasvn.log.trace";

    public JavaSVNDebugLogger(boolean loggingEnabled, Logger log) {
      myLoggingEnabled = loggingEnabled;
      myLog = log;
    }

    public void log(final SVNLogType logType, final Throwable th, final Level logLevel) {
      if (myLoggingEnabled) {
        myLog.info(th);
      }
    }

    public void log(final SVNLogType logType, final String message, final Level logLevel) {
      if (myLoggingEnabled) {
        myLog.info(message);
      }
    }

    public void log(final SVNLogType logType, final String message, final byte[] data) {
      if (myLoggingEnabled) {
        if (data != null) {
          try {
            myLog.info(message + "\n" + new String(data, "UTF-8"));
          }
          catch (UnsupportedEncodingException e) {
            myLog.info(message + "\n" + new String(data));
          }
        } else {
          myLog.info(message);
        }
      }
    }
  }

  public FileStatus[] getProvidedStatuses() {
    return new FileStatus[]{SvnFileStatus.EXTERNAL,
      SvnFileStatus.OBSTRUCTED,
      SvnFileStatus.REPLACED};
  }


  @Override @NotNull
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
    final VirtualFile child = dir.findChild(".svn");
    return child != null && child.isDirectory();
  }

  @NotNull
  public SvnFileUrlMapping getSvnFileUrlMapping() {
    return myRootsInfo;
  }

  public List<WCInfo> getAllWcInfos() {
    final SvnFileUrlMapping urlMapping = getSvnFileUrlMapping();

    final Map<String,SvnFileUrlMapping.RootUrlInfo> wcInfos = urlMapping.getAllWcInfos();
    final List<WCInfo> infos = new ArrayList<WCInfo>();
    for (Map.Entry<String, SvnFileUrlMapping.RootUrlInfo> entry : wcInfos.entrySet()) {
      final SvnFileUrlMapping.RootUrlInfo value = entry.getValue();
      final File file = new File(entry.getKey());
      infos.add(new WCInfo(entry.getKey(), value.getAbsoluteUrlAsUrl(),
                           SvnFormatSelector.getWorkingCopyFormat(file), value.getRepositoryUrl(), SvnUtil.isWorkingCopyRoot(file)));
    }
    return infos;
  }

  private class SvnWorkingCopyChecker {
    private List<WCInfo> myAllWcInfos;

    public boolean upgradeNeeded() {
      myAllWcInfos = getAllWcInfos();
      for (WCInfo info : myAllWcInfos) {
        if (! WorkingCopyFormat.ONE_DOT_FIVE.equals(info.getFormat())) {
          return true;
        }
      }
      return false;
    }

    public void doUpgrade() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final SvnFormatWorker formatWorker = new SvnFormatWorker(myProject, WorkingCopyFormat.ONE_DOT_FIVE, myAllWcInfos);
          // additionally ask about working copies with roots above the project root
          formatWorker.checkForOutsideCopies();
          if (formatWorker.haveStuffToConvert()) {
            ProgressManager.getInstance().run(formatWorker);
          }
        }
      });
    }
  }

  @Override
  public RootsConvertor getCustomConvertor() {
    return myRootsInfo;
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

  public void pathChanged(final File from, final File to) throws SVNException {
    myChangeListListener.pathChanged(from, to);
  }
}
