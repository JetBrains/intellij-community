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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.annotate.SvnAnnotationProvider;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.status.SvnStatusEnvironment;
import org.jetbrains.idea.svn.update.AbstractSvnUpdateIntegrateEnvironment;
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
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
public class SvnVcs extends AbstractVcs implements ProjectComponent {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnVcs");
  private static final Key<SVNStatusHolder> STATUS_KEY = Key.create("svn.status");
  private static final Key<SVNInfoHolder> INFO_KEY = Key.create("svn.info");

  private SvnConfiguration myConfiguration;
  private SvnEntriesFileListener myEntriesFileListener;
  private Project myProject;

  private final SvnFileStatusProvider myFileStatusProvider;
  private final SvnCheckinEnvironment myCheckinEnvironment;
  private final AbstractSvnUpdateIntegrateEnvironment mySvnUpdateEnvironment;
  private final AbstractSvnUpdateIntegrateEnvironment mySvnIntegrateEnvironment;
  private final SvnHistoryProvider mySvnHistoryProvider;
  private final SvnStatusEnvironment mySvnStatusEnvironment;
  private final SvnAnnotationProvider myAnnotationProvider;
  private final SvnDiffProvider mySvnDiffProvider;
  private VcsShowConfirmationOption myAddConfirmation;
  private SvnEditFileProvider myEditFilesProvider;

  @NonNls public static final String LOG_PARAMETER_NAME = "javasvn.log";
  public static final String pathToEntries = SvnUtil.SVN_ADMIN_DIR_NAME + File.separatorChar + SvnUtil.ENTRIES_FILE_NAME;

  static {
    //noinspection UseOfArchaicSystemPropertyAccessors
    SVNDebugLog.setDefaultLog(new JavaSVNDebugLogger(Boolean.getBoolean(LOG_PARAMETER_NAME), LOG));

    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
    FSRepositoryFactory.setup();
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
    dumpFileStatus(SvnFileStatus.SWITCHED);
    dumpFileStatus(SvnFileStatus.EXTERNAL);
    dumpFileStatus(SvnFileStatus.OBSTRUCTED);

    myFileStatusProvider = new SvnFileStatusProvider(this);
    myCheckinEnvironment = new SvnCheckinEnvironment(this);

    mySvnUpdateEnvironment = new SvnUpdateEnvironment(this);
    mySvnIntegrateEnvironment = new SvnIntegrateEnvironment(this);
    mySvnHistoryProvider = new SvnHistoryProvider(this);
    mySvnStatusEnvironment = new SvnStatusEnvironment(this);
    myEntriesFileListener = new SvnEntriesFileListener(project);
    myAnnotationProvider = new SvnAnnotationProvider(this);
    mySvnDiffProvider = new SvnDiffProvider(this);
    myEditFilesProvider = new SvnEditFileProvider(this);

  }

  public VcsShowConfirmationOption getAddConfirmation() {
    return myAddConfirmation;
  }

  public EditFileProvider getEditFileProvider() {
    return myEditFilesProvider;
  }


  public ChangeProvider getChangeProvider() {
    return new SvnChangeProvider(this);
  }

  public SVNRepository createRepository(String url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(myProject));
    return repos;
  }

  public SVNUpdateClient createUpdateClient() throws SVNException {
    return new SVNUpdateClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNStatusClient createStatusClient() throws SVNException {
    return new SVNStatusClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNWCClient createWCClient() throws SVNException {
    return new SVNWCClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNCopyClient createCopyClient() throws SVNException {
    return new SVNCopyClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNLogClient createLogClient() throws SVNException {
    return new SVNLogClient(myConfiguration.getAuthenticationManager(myProject), myConfiguration.getOptions(myProject));
  }

  public SVNCommitClient createCommitClient() throws SVNException {
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
    return mySvnIntegrateEnvironment;
  }

  public UpdateEnvironment getUpdateEnvironment() {
    return mySvnUpdateEnvironment;
  }

  public String getName() {
    LOG.debug("getName");
    return "svn";
  }

  public String getDisplayName() {
    LOG.debug("getDisplayName");
    return "Subversion";
  }

  public Configurable getConfigurable() {
    LOG.debug("createConfigurable");
    return new SvnConfigurable(myProject);
  }


  public void projectClosed() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myEntriesFileListener);
    new DefaultSVNRepositoryPool(null, null).shutdownConnections(true);
  }

  public void projectOpened() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(getProject());
    myAddConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    VirtualFileManager.getInstance().addVirtualFileListener(myEntriesFileListener);
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getComponentName() {
    return "Subversion";
  }

  public Project getProject() {
    return myProject;
  }

  public SvnConfiguration getSvnConfiguration() {
    return myConfiguration;
  }

  public static SvnVcs getInstance(Project project) {
    return project.getComponent(SvnVcs.class);
  }

  public FileStatusProvider getFileStatusProvider() {
    return myFileStatusProvider;
  }

  @NotNull
  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  public VcsHistoryProvider getVcsHistoryProvider() {
    return mySvnHistoryProvider;
  }

  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return mySvnHistoryProvider;
  }

  public UpdateEnvironment getStatusEnvironment() {
    return mySvnStatusEnvironment;
  }

  public AnnotationProvider getAnnotationProvider() {
    return myAnnotationProvider;
  }

  public SvnEntriesFileListener getSvnEntriesFileListener() {
    return myEntriesFileListener;
  }

  public DiffProvider getDiffProvider() {
    return mySvnDiffProvider;
  }

  public SVNStatusHolder getCachedStatus(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    SVNStatusHolder value = vFile.getUserData(STATUS_KEY);
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
    vFile.putUserData(STATUS_KEY, new SVNStatusHolder(entriesFile.lastModified(), vFile.getTimeStamp(), status));
  }

  public SVNInfoHolder getCachedInfo(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    SVNInfoHolder value = vFile.getUserData(INFO_KEY);
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
    vFile.putUserData(INFO_KEY, new SVNInfoHolder(entriesFile.lastModified(), vFile.getTimeStamp(), info));
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
      return status != null && !(status.getContentsStatus() == SVNStatusType.STATUS_ADDED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED);
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
      SvnFileStatus.REPLACED,
      SvnFileStatus.SWITCHED};
  }

  @Override
  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }
}
