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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.annotate.SvnAnnotationProvider;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.rollback.SvnRollbackEnvironment;
import org.jetbrains.idea.svn.update.SvnIntegrateEnvironment;
import org.jetbrains.idea.svn.update.SvnUpdateEnvironment;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.util.SVNLogInputStream;
import org.tmatesoft.svn.core.internal.util.SVNLogOutputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class SvnVcs extends AbstractVcs {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnVcs");
  private final Map<VirtualFile, SVNStatusHolder> myStatuses = new HashMap<VirtualFile, SVNStatusHolder>();
  private final Map<VirtualFile, SVNInfoHolder> myInfos = new HashMap<VirtualFile, SVNInfoHolder>();

  private SvnConfiguration myConfiguration;
  private SvnEntriesFileListener myEntriesFileListener;
  private Project myProject;

  private CheckinEnvironment myCheckinEnvironment;
  private RollbackEnvironment myRollbackEnvironment;
  private UpdateEnvironment mySvnUpdateEnvironment;
  private UpdateEnvironment mySvnIntegrateEnvironment;
  private VcsHistoryProvider mySvnHistoryProvider;
  private AnnotationProvider myAnnotationProvider;
  private DiffProvider mySvnDiffProvider;
  private VcsShowConfirmationOption myAddConfirmation;
  private VcsShowConfirmationOption myDeleteConfirmation;
  private EditFileProvider myEditFilesProvider;
  private CommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> myCommittedChangesProvider;
  private VcsShowSettingOption myCheckoutOptions;

  private ChangeProvider myChangeProvider;
  @NonNls public static final String LOG_PARAMETER_NAME = "javasvn.log";
  @NonNls public static final String VCS_NAME = "svn";
  public static final String pathToEntries = SvnUtil.SVN_ADMIN_DIR_NAME + File.separatorChar + SvnUtil.ENTRIES_FILE_NAME;

  static {
    //noinspection UseOfArchaicSystemPropertyAccessors
    SVNDebugLog.setDefaultLog(new JavaSVNDebugLogger(Boolean.getBoolean(LOG_PARAMETER_NAME), LOG));
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
    myProject = project;
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
  }

  @Override
  public void activate() {
    super.activate();
    SvnApplicationSettings.getInstance().svnActivated();
    VirtualFileManager.getInstance().addVirtualFileListener(myEntriesFileListener);
  }

  @Override
  public void deactivate() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myEntriesFileListener);
    SvnApplicationSettings.getInstance().svnDeactivated();
    new DefaultSVNRepositoryPool(null, null).shutdownConnections(true);
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

  public RollbackEnvironment getRollbackEnvironment() {
    if (myRollbackEnvironment == null) {
      myRollbackEnvironment = new SvnRollbackEnvironment(this);
    }
    return myRollbackEnvironment;
  }

  public VcsHistoryProvider getVcsHistoryProvider() {
    if (mySvnHistoryProvider == null) {
      mySvnHistoryProvider = new SvnHistoryProvider(this);
    }
    return mySvnHistoryProvider;
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
    if (value != null && value.getEntriesTimestamp() == entriesFile.lastModified() &&
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

    public void info(String message) {
      if (myLoggingEnabled) {
        myLog.info(message);
      }
    }

    public void error(String message) {
      if (myLoggingEnabled) {
        myLog.info(message);
      }
    }

    public void info(Throwable th) {
      if (myLoggingEnabled) {
        myLog.info(th);
      }
    }

    public void error(Throwable th) {
      if (myLoggingEnabled) {
        myLog.info(th);
      }
    }

    public void log(String message, byte[] data) {
      if (myLoggingEnabled) {
        myLog.info(message + " : " + new String(data == null ? ArrayUtil.EMPTY_BYTE_ARRAY : data));
      }
    }

    public InputStream createLogStream(InputStream is) {
      if (myLoggingEnabled && booleanProperty(TRACE_LOG_PARAMETER_NAME)) {
          //noinspection IOResourceOpenedButNotSafelyClosed
          //noinspection UseOfArchaicSystemPropertyAccessors
        return new SVNLogInputStream(is, this);
      }
      return is;
    }

    public OutputStream createLogStream(OutputStream os) {
      if (myLoggingEnabled && booleanProperty(TRACE_LOG_PARAMETER_NAME)) {
          //noinspection IOResourceOpenedButNotSafelyClosed
          //noinspection UseOfArchaicSystemPropertyAccessors
        return new SVNLogOutputStream(os, this);
      }
      return os;
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
  public boolean isVersionedDirectory(final VirtualFile dir) {
    final VirtualFile child = dir.findChild(".svn");
    return child != null && child.isDirectory();
  }
}
