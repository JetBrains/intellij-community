/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.status;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class Status {
  private SVNURL myURL;
  private File myFile;
  private SVNNodeKind myKind;
  private SVNRevision myRevision;
  private SVNRevision myCommittedRevision;
  private SVNStatusType myContentsStatus;
  private SVNStatusType myPropertiesStatus;
  private SVNStatusType myRemoteContentsStatus;
  private SVNStatusType myRemoteNodeStatus;
  private SVNStatusType myRemotePropertiesStatus;
  private boolean myIsLocked;
  private boolean myIsCopied;
  private boolean myIsSwitched;
  private String myCopyFromURL;
  private SVNLock myRemoteLock;
  private SVNLock myLocalLock;
  private SVNRevision myRemoteRevision;
  private String myChangelistName;
  private TreeConflictDescription myTreeConflict;
  private boolean myIsConflicted;

  private SVNStatusType myNodeStatus;
  private SVNURL myRepositoryRootURL;

  @NotNull
  public static Status create(@NotNull SVNStatus status) {
    return new Status(status.getURL(), status.getFile(), status.getKind(), status.getRevision(), status.getCommittedRevision(),
                      status.getContentsStatus(), status.getPropertiesStatus(),
                      status.getRemoteContentsStatus(), status.getRemotePropertiesStatus(), status.isLocked(), status.isCopied(),
                      status.isSwitched(),
                      status.getCopyFromURL(),
                      status.getRemoteLock(), status.getLocalLock(), status.getChangelistName(),
                      TreeConflictDescription.create(status.getTreeConflict()));
  }

  public Status(SVNURL url,
                File file,
                SVNNodeKind kind,
                SVNRevision revision,
                SVNRevision committedRevision,
                SVNStatusType contentsStatus,
                SVNStatusType propertiesStatus,
                SVNStatusType remoteContentsStatus,
                SVNStatusType remotePropertiesStatus,
                boolean isLocked,
                boolean isCopied,
                boolean isSwitched,
                String copyFromURL,
                SVNLock remoteLock,
                SVNLock localLock,
                String changelistName,
                TreeConflictDescription treeConflict) {
    myURL = url;
    myFile = file;
    myKind = kind == null ? SVNNodeKind.NONE : kind;
    myRevision = revision == null ? SVNRevision.UNDEFINED : revision;
    myCommittedRevision = committedRevision == null ? SVNRevision.UNDEFINED : committedRevision;
    myContentsStatus = contentsStatus == null ? SVNStatusType.STATUS_NONE : contentsStatus;
    myPropertiesStatus = propertiesStatus == null ? SVNStatusType.STATUS_NONE : propertiesStatus;
    myRemoteContentsStatus = remoteContentsStatus == null ? SVNStatusType.STATUS_NONE : remoteContentsStatus;
    myRemotePropertiesStatus = remotePropertiesStatus == null ? SVNStatusType.STATUS_NONE : remotePropertiesStatus;
    myRemoteNodeStatus = SVNStatusType.STATUS_NONE;
    myIsLocked = isLocked;
    myIsCopied = isCopied;
    myIsSwitched = isSwitched;
    myCopyFromURL = copyFromURL;
    myRemoteLock = remoteLock;
    myLocalLock = localLock;
    myChangelistName = changelistName;
    myTreeConflict = treeConflict;
    myRemoteRevision = SVNRevision.UNDEFINED;
  }

  public Status() {
    setRevision(SVNRevision.UNDEFINED);
    myRemoteRevision = SVNRevision.UNDEFINED;
  }

  public SVNURL getURL() {
    return myURL;
  }

  public File getFile() {
    return myFile;
  }

  public SVNNodeKind getKind() {
    return myKind;
  }

  public SVNRevision getRevision() {
    return myRevision;
  }

  public SVNRevision getCommittedRevision() {
    return myCommittedRevision;
  }

  public SVNStatusType getContentsStatus() {
    return myContentsStatus;
  }

  public SVNStatusType getPropertiesStatus() {
    return myPropertiesStatus;
  }

  public SVNStatusType getRemoteContentsStatus() {
    return myRemoteContentsStatus;
  }

  public SVNStatusType getRemotePropertiesStatus() {
    return myRemotePropertiesStatus;
  }

  public boolean isLocked() {
    return myIsLocked;
  }

  public boolean isCopied() {
    return myIsCopied;
  }

  public boolean isSwitched() {
    return myIsSwitched;
  }

  public String getCopyFromURL() {
    return myCopyFromURL;
  }

  public SVNLock getRemoteLock() {
    return myRemoteLock;
  }

  public SVNLock getLocalLock() {
    return myLocalLock;
  }

  public SVNRevision getRemoteRevision() {
    return myRemoteRevision;
  }

  public String getChangelistName() {
    return myChangelistName;
  }

  public TreeConflictDescription getTreeConflict() {
    return myTreeConflict;
  }

  public boolean isConflicted() {
    return myIsConflicted;
  }

  public SVNStatusType getRemoteNodeStatus() {
    return myRemoteNodeStatus;
  }

  public SVNStatusType getNodeStatus() {
    if (myNodeStatus == null) {
      return myContentsStatus;
    }
    return myNodeStatus;
  }

  public SVNURL getRepositoryRootURL() {
    return myRepositoryRootURL;
  }

  public void setURL(SVNURL uRL) {
    myURL = uRL;
  }

  public void setFile(File file) {
    myFile = file;
  }

  public void setKind(SVNNodeKind kind) {
    myKind = kind;
  }

  public void setRevision(SVNRevision revision) {
    myRevision = revision;
  }

  public void setCommittedRevision(SVNRevision committedRevision) {
    myCommittedRevision = committedRevision;
  }

  public void setContentsStatus(SVNStatusType statusType) {
    myContentsStatus = statusType;
  }

  public void setPropertiesStatus(SVNStatusType propertiesStatus) {
    myPropertiesStatus = propertiesStatus;
  }

  public void setRemoteContentsStatus(SVNStatusType remoteContentsStatus) {
    myRemoteContentsStatus = remoteContentsStatus;
  }

  public void setRemotePropertiesStatus(SVNStatusType remotePropertiesStatus) {
    myRemotePropertiesStatus = remotePropertiesStatus;
  }

  public void setIsLocked(boolean isLocked) {
    myIsLocked = isLocked;
  }

  public void setIsCopied(boolean isCopied) {
    myIsCopied = isCopied;
  }

  public void setIsSwitched(boolean isSwitched) {
    myIsSwitched = isSwitched;
  }

  public void setRemoteLock(SVNLock remoteLock) {
    myRemoteLock = remoteLock;
  }

  public void setLocalLock(SVNLock localLock) {
    myLocalLock = localLock;
  }

  public void setChangelistName(String changelistName) {
    myChangelistName = changelistName;
  }

  public void setIsConflicted(boolean isConflicted) {
    myIsConflicted = isConflicted;
  }
}
