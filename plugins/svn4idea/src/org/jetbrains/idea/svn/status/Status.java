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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.lock.Lock;
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
  private @NotNull NodeKind myKind;
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
  @Nullable private Lock myRemoteLock;
  @Nullable private Lock myLocalLock;
  private SVNRevision myRemoteRevision;
  private String myChangelistName;
  @Nullable private TreeConflictDescription myTreeConflict;
  private boolean myIsConflicted;

  private SVNStatusType myNodeStatus;
  private SVNURL myRepositoryRootURL;

  @Nullable
  public static Status create(@Nullable SVNStatus status) {
    Status result = null;

    if (status != null) {
      result = new Status(status.getURL(), status.getFile(), NodeKind.from(status.getKind()), status.getRevision(),
                          status.getCommittedRevision(), status.getContentsStatus(), status.getPropertiesStatus(),
                          status.getRemoteContentsStatus(), status.getRemotePropertiesStatus(), status.isLocked(),
                          status.isCopied(), status.isSwitched(), status.getCopyFromURL(), Lock.create(status.getRemoteLock()),
                          Lock.create(status.getLocalLock()), status.getChangelistName(),
                          TreeConflictDescription.create(status.getTreeConflict()));
      result.setIsConflicted(status.isConflicted());
      result.setNodeStatus(status.getNodeStatus());
      result.setRemoteNodeStatus(status.getRemoteNodeStatus());
      result.setRemoteRevision(status.getRemoteRevision());
      result.setRepositoryRootURL(status.getRepositoryRootURL());
    }

    return result;
  }

  public Status(SVNURL url,
                File file,
                @NotNull NodeKind kind,
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
                @Nullable Lock remoteLock,
                @Nullable Lock localLock,
                String changelistName,
                @Nullable TreeConflictDescription treeConflict) {
    myURL = url;
    myFile = file;
    myKind = kind;
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

  @NotNull
  public NodeKind getKind() {
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

  @Nullable
  public Lock getRemoteLock() {
    return myRemoteLock;
  }

  @Nullable
  public Lock getLocalLock() {
    return myLocalLock;
  }

  public SVNRevision getRemoteRevision() {
    return myRemoteRevision;
  }

  public String getChangelistName() {
    return myChangelistName;
  }

  @Nullable
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

  public void setKind(@NotNull NodeKind kind) {
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

  public void setRemoteLock(@Nullable Lock remoteLock) {
    myRemoteLock = remoteLock;
  }

  public void setLocalLock(@Nullable Lock localLock) {
    myLocalLock = localLock;
  }

  public void setChangelistName(String changelistName) {
    myChangelistName = changelistName;
  }

  public void setIsConflicted(boolean isConflicted) {
    myIsConflicted = isConflicted;
  }

  public void setRemoteNodeStatus(SVNStatusType remoteNodeStatus) {
    myRemoteNodeStatus = remoteNodeStatus;
  }

  public void setNodeStatus(SVNStatusType nodeStatus) {
    myNodeStatus = nodeStatus;
  }

  public void setRepositoryRootURL(SVNURL repositoryRootURL) {
    myRepositoryRootURL = repositoryRootURL;
  }

  public void setRemoteRevision(SVNRevision remoteRevision) {
    myRemoteRevision = remoteRevision;
  }
}
