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
 * TODO: Could also inherit BaseNodeDescription when myKind becomes final.
 */
public class Status {
  private Url myURL;
  private File myFile;
  private String myPath;
  private boolean myFileExists;
  private @NotNull NodeKind myKind;
  @NotNull private Revision myRevision;
  @NotNull private Revision myCommittedRevision;
  private StatusType myContentsStatus;
  private StatusType myPropertiesStatus;
  private StatusType myRemoteContentsStatus;
  private StatusType myRemotePropertiesStatus;
  private boolean myIsLocked;
  private boolean myIsCopied;
  private boolean myIsSwitched;
  @Nullable private Lock myRemoteLock;
  @Nullable private Lock myLocalLock;
  private String myChangelistName;
  private boolean myIsConflicted;
  private Info myInfo;
  private Getter<Info> myInfoGetter;

  public Status() {
    setRevision(Revision.UNDEFINED);
    myInfoGetter = () -> null;
    setCommittedRevision(Revision.UNDEFINED);
  }

  public Url getURL() {
    Url url = myURL;

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

  public String getPath() {
    return myPath;
  }

  public void setInfoGetter(Getter<Info> infoGetter) {
    myInfoGetter = infoGetter;
  }

  private Info initInfo() {
    if (myInfo == null) {
      final StatusType contentsStatus = getContentsStatus();
      if (contentsStatus == null || StatusType.UNKNOWN.equals(contentsStatus)) {
        return null;
      }
      myInfo = myInfoGetter.get();
    }
    return myInfo;
  }

  public Info getInfo() {
    return initInfo();
  }

  @NotNull
  public NodeKind getKind() {
    if (myFileExists) return myKind;
    Info info = initInfo();
    return info != null ? info.getNodeKind() : myKind;
  }

  @NotNull
  public Revision getRevision() {
    final Revision revision = myRevision;
    if (revision.isValid()) return revision;

    final StatusType status = getContentsStatus();
    if (StatusType.STATUS_NONE.equals(status) || StatusType.STATUS_UNVERSIONED.equals(status) ||
        StatusType.STATUS_ADDED.equals(status)) {
      return revision;
    }

    final Info info = initInfo();
    return info == null ? revision : info.getRevision();
  }

  @NotNull
  public Revision getCommittedRevision() {
    return myCommittedRevision;
  }

  public StatusType getContentsStatus() {
    return myContentsStatus;
  }

  public StatusType getPropertiesStatus() {
    return myPropertiesStatus;
  }

  public StatusType getRemoteContentsStatus() {
    return myRemoteContentsStatus;
  }

  public StatusType getRemotePropertiesStatus() {
    return myRemotePropertiesStatus;
  }

  public boolean is(@NotNull StatusType type) {
    return type.equals(getContentsStatus());
  }

  public boolean is(@NotNull StatusType... types) {
    return ContainerUtil.or(types, type -> is(type));
  }

  public boolean isProperty(@NotNull StatusType type) {
    return type.equals(getPropertiesStatus());
  }

  public boolean isProperty(@NotNull StatusType... types) {
    return ContainerUtil.or(types, type -> isProperty(type));
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

  @Nullable
  public Url getCopyFromURL() {
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

  public String getChangelistName() {
    return myChangelistName;
  }

  @Nullable
  public TreeConflictDescription getTreeConflict() {
    if (!isConflicted()) return null;
    Info info = initInfo();
    return info != null ? info.getTreeConflict() : null;
  }

  public boolean isConflicted() {
    return myIsConflicted;
  }

  @Nullable
  public Url getRepositoryRootURL() {
    Info info = initInfo();
    return info != null ? info.getRepositoryRootUrl() : null;
  }

  public void setURL(Url uRL) {
    myURL = uRL;
  }

  public void setFile(File file) {
    myFile = file;
  }

  public void setPath(String path) {
    myPath = path;
  }

  public void setKind(boolean exists, @NotNull NodeKind kind) {
    myFileExists = exists;
    setKind(kind);
  }

  public void setKind(@NotNull NodeKind kind) {
    myKind = kind;
  }

  public void setRevision(@NotNull Revision revision) {
    myRevision = revision;
  }

  public void setCommittedRevision(@NotNull Revision committedRevision) {
    myCommittedRevision = committedRevision;
  }

  public void setContentsStatus(StatusType statusType) {
    myContentsStatus = statusType;
  }

  public void setPropertiesStatus(StatusType propertiesStatus) {
    myPropertiesStatus = propertiesStatus;
  }

  public void setRemoteContentsStatus(StatusType remoteContentsStatus) {
    myRemoteContentsStatus = remoteContentsStatus;
  }

  public void setRemotePropertiesStatus(StatusType remotePropertiesStatus) {
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
}
