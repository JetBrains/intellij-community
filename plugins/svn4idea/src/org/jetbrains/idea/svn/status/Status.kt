// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.Getter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.lock.Lock;

import java.io.File;

/**
 * TODO: Could also inherit BaseNodeDescription when myNodeKind becomes final.
 */
public class Status {
  private Url myUrl;
  private File myFile;
  private boolean myFileExists;
  private @NotNull NodeKind myNodeKind;
  @NotNull private Revision myRevision;
  @NotNull private Revision myCommittedRevision;
  private StatusType myItemStatus;
  private StatusType myPropertyStatus;
  private StatusType myRemoteItemStatus;
  private StatusType myRemotePropertyStatus;
  private boolean myIsWorkingCopyLocked;
  private boolean myIsCopied;
  private boolean myIsSwitched;
  @Nullable private Lock myRemoteLock;
  @Nullable private Lock myLocalLock;
  private String myChangeListName;
  private boolean myIsTreeConflicted;
  private Info myInfo;
  private Getter<Info> myInfoProvider;

  public Status() {
    setRevision(Revision.UNDEFINED);
    myInfoProvider = () -> null;
    setCommittedRevision(Revision.UNDEFINED);
  }

  public Url getUrl() {
    Url url = myUrl;

    if (url == null) {
      Info info = initInfo();
      url = info != null ? info.getUrl() : url;
    }

    return url;
  }

  public File getFile() {
    File file = myFile;

    if (file == null) {
      Info info = initInfo();
      file = info != null ? info.getFile() : file;
    }

    return file;
  }

  public void setInfoProvider(Getter<Info> infoProvider) {
    myInfoProvider = infoProvider;
  }

  private Info initInfo() {
    if (myInfo == null) {
      final StatusType itemStatus = getItemStatus();
      if (itemStatus == null || StatusType.STATUS_NONE.equals(itemStatus)) {
        return null;
      }
      myInfo = myInfoProvider.get();
    }
    return myInfo;
  }

  public Info getInfo() {
    return initInfo();
  }

  @NotNull
  public NodeKind getNodeKind() {
    if (myFileExists) return myNodeKind;
    Info info = initInfo();
    return info != null ? info.getNodeKind() : myNodeKind;
  }

  @NotNull
  public Revision getRevision() {
    final Revision revision = myRevision;
    if (revision.isValid()) return revision;

    if (is(StatusType.STATUS_NONE, StatusType.STATUS_UNVERSIONED, StatusType.STATUS_ADDED)) {
      return revision;
    }

    final Info info = initInfo();
    return info == null ? revision : info.getRevision();
  }

  @NotNull
  public Revision getCommittedRevision() {
    return myCommittedRevision;
  }

  public StatusType getItemStatus() {
    return myItemStatus;
  }

  public StatusType getPropertyStatus() {
    return myPropertyStatus;
  }

  public StatusType getRemoteItemStatus() {
    return myRemoteItemStatus;
  }

  public StatusType getRemotePropertyStatus() {
    return myRemotePropertyStatus;
  }

  public boolean is(@NotNull StatusType type) {
    return type.equals(getItemStatus());
  }

  public boolean is(@NotNull StatusType... types) {
    return ContainerUtil.or(types, type -> is(type));
  }

  public boolean isProperty(@NotNull StatusType type) {
    return type.equals(getPropertyStatus());
  }

  public boolean isProperty(@NotNull StatusType... types) {
    return ContainerUtil.or(types, type -> isProperty(type));
  }

  public boolean isWorkingCopyLocked() {
    return myIsWorkingCopyLocked;
  }

  public boolean isCopied() {
    return myIsCopied;
  }

  public boolean isSwitched() {
    return myIsSwitched;
  }

  @Nullable
  public Url getCopyFromUrl() {
    if (!isCopied()) return null;
    Info info = initInfo();
    return info != null ? info.getCopyFromUrl() : null;
  }

  @Nullable
  public Lock getRemoteLock() {
    return myRemoteLock;
  }

  @Nullable
  public Lock getLocalLock() {
    return myLocalLock;
  }

  public String getChangeListName() {
    return myChangeListName;
  }

  @Nullable
  public TreeConflictDescription getTreeConflict() {
    if (!isTreeConflicted()) return null;
    Info info = initInfo();
    return info != null ? info.getTreeConflict() : null;
  }

  public boolean isTreeConflicted() {
    return myIsTreeConflicted;
  }

  @Nullable
  public Url getRepositoryRootUrl() {
    Info info = initInfo();
    return info != null ? info.getRepositoryRootUrl() : null;
  }

  public void setUrl(Url url) {
    myUrl = url;
  }

  public void setFile(File file) {
    myFile = file;
  }

  public void setNodeKind(boolean exists, @NotNull NodeKind nodeKind) {
    myFileExists = exists;
    setNodeKind(nodeKind);
  }

  public void setNodeKind(@NotNull NodeKind nodeKind) {
    myNodeKind = nodeKind;
  }

  public void setRevision(@NotNull Revision revision) {
    myRevision = revision;
  }

  public void setCommittedRevision(@NotNull Revision committedRevision) {
    myCommittedRevision = committedRevision;
  }

  public void setItemStatus(StatusType statusType) {
    myItemStatus = statusType;
  }

  public void setPropertyStatus(StatusType propertyStatus) {
    myPropertyStatus = propertyStatus;
  }

  public void setRemoteItemStatus(StatusType remoteItemStatus) {
    myRemoteItemStatus = remoteItemStatus;
  }

  public void setRemotePropertyStatus(StatusType remotePropertyStatus) {
    myRemotePropertyStatus = remotePropertyStatus;
  }

  public void setWorkingCopyLocked(boolean isWorkingCopyLocked) {
    myIsWorkingCopyLocked = isWorkingCopyLocked;
  }

  public void setCopied(boolean isCopied) {
    myIsCopied = isCopied;
  }

  public void setSwitched(boolean isSwitched) {
    myIsSwitched = isSwitched;
  }

  public void setRemoteLock(@Nullable Lock remoteLock) {
    myRemoteLock = remoteLock;
  }

  public void setLocalLock(@Nullable Lock localLock) {
    myLocalLock = localLock;
  }

  public void setChangeListName(String changeListName) {
    myChangeListName = changeListName;
  }

  public void setTreeConflicted(boolean isTreeConflicted) {
    myIsTreeConflicted = isTreeConflicted;
  }
}
