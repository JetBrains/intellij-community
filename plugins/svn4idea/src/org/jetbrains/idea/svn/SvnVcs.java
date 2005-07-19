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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.idea.svn.annotate.SvnAnnotationProvider;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.jetbrains.idea.svn.status.SvnStatusEnvironment;
import org.jetbrains.idea.svn.update.SvnUpdateEnvironment;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.DebugDefaultLogger;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.LoggingInputStream;
import org.tmatesoft.svn.util.LoggingOutputStream;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class SvnVcs extends AbstractVcs implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnVcs");

  private SvnConfiguration myConfiguration;
  private SvnEntriesFileListener myEntriesFileListener;
  private Project myProject;

  private final SvnFileStatusProvider myFileStatusProvider;
  private final SvnCheckinEnvironment myCheckinEnvironment;
  private final SvnUpdateEnvironment mySvnUpdateEnvironment;
  private final SvnHistoryProvider mySvnHistoryProvider;
  private final SvnStatusEnvironment mySvnStatusEnvironment;
  private final SvnUpToDateRevisionProvider mySvnUpToDateRevisionProvider;
  private final SvnAnnotationProvider myAnnotationProvider;
  private final SvnDiffProvider mySvnDiffProvider;
  private VcsShowConfirmationOption myAddConfirmation;
  private SvnEditFileProvider myEditFilesProvider;

  static {
    DebugLog.setLogger(new JavaSVNDebugLogger(Boolean.getBoolean("javasvn.log"), LOG));

    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
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
    mySvnHistoryProvider = new SvnHistoryProvider(this);
    mySvnStatusEnvironment = new SvnStatusEnvironment(this);
    mySvnUpToDateRevisionProvider = new SvnUpToDateRevisionProvider();
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

  public SVNRepository createRepository(String url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
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

  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  public VcsHistoryProvider getVcsHistoryProvider() {
    return mySvnHistoryProvider;
  }

  public UpdateEnvironment getStatusEnvironment() {
    return mySvnStatusEnvironment;
  }

  public UpToDateRevisionProvider getUpToDateRevisionProvider() {
    return mySvnUpToDateRevisionProvider;
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

  public boolean fileExistsInVcs(FilePath path) {
    File file = path.getIOFile();
    try {
      SVNStatus status = createStatusClient().doStatus(file, false);
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
    try {
      SVNStatus status = createStatusClient().doStatus(file, false);
      return status != null && !(status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
                                 status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED);
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  private static class JavaSVNDebugLogger extends DebugDefaultLogger {
    private final boolean myLoggingEnabled;
    private final Logger myLog;

    public JavaSVNDebugLogger(boolean loggingEnabled, Logger log) {
      myLoggingEnabled = loggingEnabled;
      myLog = log;
    }

    public void logFine(String message) {
      if (!myLoggingEnabled) {
        return;
      }
      message = message == null ? "" : message;
      myLog.info(message);
    }

    public void logInfo(String message) {
      if (!myLoggingEnabled) {
        return;
      }
      message = message == null ? "" : message;
      myLog.info(message);
    }

    public void logError(String message, Throwable th) {
      if (!myLoggingEnabled) {
        return;
      }
      message = message == null ? "" : message;
      if (th != null) {
        myLog.info(message, th);
      }
      else {
        myLog.info(message);
      }
    }

    public boolean isFineEnabled() {
      return myLoggingEnabled;
    }

    public boolean isInfoEnabled() {
      return myLoggingEnabled;
    }

    public boolean isErrorEnabled() {
      return myLoggingEnabled;
    }

    public LoggingInputStream getLoggingInputStream(String protocol, InputStream stream) {
      protocol = protocol == null ? "svn" : protocol;
      final boolean enabled = Boolean.getBoolean("javasvn.log." + protocol);
      return new LoggingInputStream(stream, enabled ? this : null);
    }

    public LoggingOutputStream getLoggingOutputStream(String protocol, OutputStream stream) {
      protocol = protocol == null ? "svn" : protocol;
      final boolean enabled = Boolean.getBoolean("javasvn.log." + protocol);
      return new LoggingOutputStream(stream, enabled ? this : null);
    }

    public void logStream(String content, boolean writeNotRead) {
      if (!myLoggingEnabled) {
        return;
      }
      content = content == null ? "" : content;
      content = writeNotRead ? "SENT:" + content : "READ" + content;
      myLog.info(content);
    }
  }

  public FileStatus[] getProvidedStatuses() {
    return new FileStatus[]{
      SvnFileStatus.EXTERNAL,
      SvnFileStatus.OBSTRUCTED,
      SvnFileStatus.REPLACED,
      SvnFileStatus.SWITCHED      
    };
  }
}
 