// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.lock.Lock;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * TODO: Merge PortableStatus and Status to single class.
 *
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 */
public class PortableStatus extends Status {

  private boolean myConflicted;
  private Getter<Info> myInfoGetter;
  private Info myInfo;
  private String myPath;
  private boolean myFileExists;

  /**
   * Constructs an <b>SVNStatus</b> object filling it with status information
   * details.
   * <p/>
   * <p/>
   * Used by SVNKit internals to construct and initialize an <b>SVNStatus</b>
   * object. It's not intended for users (from an API point of view).
   *
   * @param url                    item's repository location
   * @param file                   item's path in a File representation
   * @param kind                   item's node kind
   * @param revision               item's working revision
   * @param committedRevision      item's last changed revision
   * @param committedDate          item's last changed date
   * @param author                 item's last commit author
   * @param contentsStatus         local status of item's contents
   * @param propertiesStatus       local status of item's properties
   * @param remoteContentsStatus   status of item's contents against a repository
   * @param remotePropertiesStatus status of item's properties against a repository
   * @param isLocked               if the item is locked by the driver (not a user lock)
   * @param isCopied               if the item is added with history
   * @param isSwitched             if the item is switched to a different URL
   * @param isFileExternal         tells if the item is an external file
   * @param conflictNewFile        temp file with latest changes from the repository
   * @param conflictOldFile        temp file just as the conflicting one was at the BASE revision
   * @param conflictWrkFile        temp file with all user's current local modifications
   * @param projRejectFile         temp file describing properties conflicts
   * @param copyFromURL            url of the item's ancestor from which the item was copied
   * @param copyFromRevision       item's ancestor revision from which the item was copied
   * @param remoteLock             item's lock in the repository
   * @param localLock              item's local lock
   * @param entryProperties        item's SVN specific '&lt;entry' properties
   * @param changelistName         changelist name which the item belongs to
   * @param wcFormatVersion        working copy format number
   * @param treeConflict           tree conflict description
   * @since 1.3
   */
  public PortableStatus(Url url,
                        File file,
                        @NotNull NodeKind kind,
                        Revision revision,
                        Revision committedRevision,
                        Date committedDate,
                        String author,
                        StatusType contentsStatus,
                        StatusType propertiesStatus,
                        StatusType remoteContentsStatus,
                        StatusType remotePropertiesStatus,
                        boolean isLocked,
                        boolean isCopied,
                        boolean isSwitched,
                        boolean isFileExternal,
                        @Nullable Lock remoteLock,
                        @Nullable Lock localLock,
                        Map entryProperties,
                        String changelistName,
                        int wcFormatVersion,
                        boolean isConflicted,
                        Getter<Info> infoGetter) {
    super(url, file, kind, revision, committedRevision, contentsStatus, propertiesStatus, remoteContentsStatus,
          remotePropertiesStatus, isLocked, isCopied, isSwitched, null, remoteLock,
          localLock, changelistName, null);
    myConflicted = isConflicted;
    myInfoGetter = infoGetter == null ? () -> null : infoGetter;
  }

  public PortableStatus() {
    myInfoGetter = () -> null;
    setCommittedRevision(Revision.UNDEFINED);
  }

  @Override
  public void setIsConflicted(boolean isConflicted) {
    myConflicted = isConflicted;
    super.setIsConflicted(isConflicted);
  }

  public void setInfoGetter(Getter<Info> infoGetter) {
    myInfoGetter = infoGetter;
  }

  @Override
  public boolean isConflicted() {
    return myConflicted;
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

  @Override
  @NotNull
  public NodeKind getKind() {
    if (myFileExists) return super.getKind();
    final Info info = initInfo();
    if (info != null) {
      return info.getKind();
    }
    return super.getKind();
  }

  /**
   * Gets the URL (repository location) of the ancestor from which the item
   * was copied. That is when the item is added with history.
   *
   * @return the item ancestor's URL
   */
  @Override
  public Url getCopyFromURL() {
    if (! isCopied()) return null;
    final Info info = initInfo();
    if (info == null) return null;
    return initInfo().getCopyFromURL();
  }

  @Override
  public Url getURL() {
    Url url = super.getURL();

    if (url == null) {
      Info info = initInfo();
      url = info != null ? info.getURL() : url;
    }

    return url;
  }

  @Override
  public Url getRepositoryRootURL() {
    Url url = super.getRepositoryRootURL();

    if (url == null) {
      Info info = initInfo();
      url = info != null ? info.getRepositoryRootURL() : url;
    }

    return url;
  }

  @Override
  public File getFile() {
    File file = super.getFile();

    if (file == null) {
      Info info = initInfo();
      file = info != null ? info.getFile() : file;
    }

    return file;
  }

  @NotNull
  @Override
  public Revision getRevision() {
    final Revision revision = super.getRevision();
    if (revision.isValid()) return revision;

    final StatusType status = getContentsStatus();
    if (StatusType.STATUS_NONE.equals(status) || StatusType.STATUS_UNVERSIONED.equals(status) ||
        StatusType.STATUS_ADDED.equals(status)) return revision;

    final Info info = initInfo();
    return info == null ? revision : info.getRevision();
  }

  /**
   * Returns a tree conflict description.
   *
   * @return tree conflict description; {@code null} if no conflict
   *         description exists on this item
   * @since 1.3
   */
  @Override
  @Nullable
  public TreeConflictDescription getTreeConflict() {
    if (! isConflicted()) return null;
    final Info info = initInfo();
    return info == null ? null : info.getTreeConflict();
  }

  public void setPath(String path) {
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  public void setKind(boolean exists, @NotNull NodeKind kind) {
    myFileExists = exists;
    setKind(kind);
  }
}
