/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.portable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 12:29 PM
 */
public class PortableStatus extends SVNStatus {

  private static final Logger LOG = Logger.getInstance(PortableStatus.class);

  private boolean myConflicted;
  private Getter<SVNInfo> myInfoGetter;
  private SVNInfo myInfo;
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
  public PortableStatus(SVNURL url,
                        File file,
                        SVNNodeKind kind,
                        SVNRevision revision,
                        SVNRevision committedRevision,
                        Date committedDate,
                        String author,
                        SVNStatusType contentsStatus,
                        SVNStatusType propertiesStatus,
                        SVNStatusType remoteContentsStatus,
                        SVNStatusType remotePropertiesStatus,
                        boolean isLocked,
                        boolean isCopied,
                        boolean isSwitched,
                        boolean isFileExternal,
                        SVNLock remoteLock,
                        SVNLock localLock,
                        Map entryProperties,
                        String changelistName,
                        int wcFormatVersion,
                        boolean isConflicted,
                        Getter<SVNInfo> infoGetter) {
    super(url, file, kind, revision, committedRevision, committedDate, author, contentsStatus, propertiesStatus, remoteContentsStatus,
          remotePropertiesStatus, isLocked, isCopied, isSwitched, isFileExternal, null, null, null, null, null, null, remoteLock,
          localLock, entryProperties, changelistName, wcFormatVersion, null);
    myConflicted = isConflicted;
    myInfoGetter = infoGetter == null ? new Getter<SVNInfo>() {
      @Override
      public SVNInfo get() {
        return null;
      }
    } : infoGetter;
  }

  public PortableStatus() {
    myInfoGetter = new Getter<SVNInfo>() {
      @Override
      public SVNInfo get() {
        return null;
      }
    };
  }

  @Override
  public int getWorkingCopyFormat() {
    LOG.error("Do not use working copy format detection through status");
    return 0;
  }

  @Override
  public void setIsConflicted(boolean isConflicted) {
    myConflicted = isConflicted;
    super.setIsConflicted(isConflicted);
  }

  public void setConflicted(boolean conflicted) {
    myConflicted = conflicted;
  }

  public void setInfoGetter(Getter<SVNInfo> infoGetter) {
    myInfoGetter = infoGetter;
  }

  @Override
  public boolean isConflicted() {
    return myConflicted;
  }

  private SVNInfo initInfo() {
    if (myInfo == null) {
      final SVNStatusType contentsStatus = getContentsStatus();
      if (contentsStatus == null || SVNStatusType.UNKNOWN.equals(contentsStatus)) {
        return null;
      }
      myInfo = myInfoGetter.get();
    }
    return myInfo;
  }

  public SVNInfo getInfo() {
    return initInfo();
  }

  @Override
  public SVNNodeKind getKind() {
    if (myFileExists) return super.getKind();
    final SVNInfo info = initInfo();
    if (info != null) {
      return info.getKind();
    }
    return super.getKind();
  }

  /**
   * Gets the temporary file that contains all latest changes from the
   * repository which led to a conflict with local changes. This file is at
   * the HEAD revision.
   *
   * @return an autogenerated temporary file just as it is in the latest
   *         revision in the repository
   */
  @Override
  public File getConflictNewFile() {
    if (! isConflicted()) return null;
    final SVNInfo info = initInfo();
    return info == null ? null : info.getConflictNewFile();
  }

  /**
   * Gets the temporary BASE revision file of that working file that is
   * currently in conflict with changes received from the repository. This
   * file does not contain the latest user's modifications, only 'pristine'
   * contents.
   *
   * @return an autogenerated temporary file just as the conflicting file was
   *         before any modifications to it
   */
  @Override
  public File getConflictOldFile() {
    if (! isConflicted()) return null;
    final SVNInfo info = initInfo();
    return info == null ? null : info.getConflictOldFile();
  }

  /**
   * Gets the temporary <i>'.mine'</i> file with all current local changes to
   * the original file. That is if the file item is in conflict with changes
   * that came during an update this temporary file is created to get the
   * snapshot of the user's file with only the user's local modifications and
   * nothing more.
   *
   * @return an autogenerated temporary file with only the user's
   *         modifications
   */
  @Override
  public File getConflictWrkFile() {
    if (! isConflicted()) return null;
    final SVNInfo info = initInfo();
    return info == null ? null : info.getConflictWrkFile();
  }

  /**
   * Gets the <i>'.prej'</i> file containing details on properties conflicts.
   * If the item's properties are in conflict with those that came during an
   * update this file will contain a conflict description.
   *
   * @return the properties conflicts file
   */
  @Override
  public File getPropRejectFile() {
    if (! isConflicted()) return null;
    final SVNInfo info = initInfo();
    return info == null ? null : info.getPropConflictFile();
  }

  /**
   * Gets the URL (repository location) of the ancestor from which the item
   * was copied. That is when the item is added with history.
   *
   * @return the item ancestor's URL
   */
  @Override
  public String getCopyFromURL() {
    if (! isCopied()) return null;
    final SVNInfo info = initInfo();
    if (info == null) return null;
    SVNURL url = initInfo().getCopyFromURL();
    return url == null ? null : url.toString();
  }

  @Override
  public SVNURL getURL() {
    SVNURL url = super.getURL();

    if (url == null) {
      SVNInfo info = initInfo();
      url = info != null ? info.getURL() : url;
    }

    return url;
  }

  @Override
  public SVNURL getRepositoryRootURL() {
    SVNURL url = super.getRepositoryRootURL();

    if (url == null) {
      SVNInfo info = initInfo();
      url = info != null ? info.getRepositoryRootURL() : url;
    }

    return url;
  }

  @Override
  public File getFile() {
    File file = super.getFile();

    if (file == null) {
      SVNInfo info = initInfo();
      file = info != null ? info.getFile() : file;
    }

    return file;
  }

  @Override
  public SVNRevision getRevision() {
    final SVNRevision revision = super.getRevision();
    if (revision != null && revision.isValid()) return revision;

    final SVNStatusType status = getContentsStatus();
    if (SVNStatusType.STATUS_NONE.equals(status) || SVNStatusType.STATUS_UNVERSIONED.equals(status) ||
        SVNStatusType.STATUS_ADDED.equals(status)) return revision;

    final SVNInfo info = initInfo();
    return info == null ? revision : info.getRevision();
  }

  /**
   * Gets the revision of the item's ancestor from which the item was copied
   * (the item is added with history).
   *
   * @return the ancestor's revision
   */
  @Override
  public SVNRevision getCopyFromRevision() {
    if (! isCopied()) return null;
    final SVNInfo info = initInfo();
    return info == null ? null : info.getCopyFromRevision();
  }

  /**
   * Returns a tree conflict description.
   *
   * @return tree conflict description; <code>null</code> if no conflict
   *         description exists on this item
   * @since 1.3
   */
  @Override
  public SVNTreeConflictDescription getTreeConflict() {
    if (! isConflicted()) return null;
    final SVNInfo info = initInfo();
    return info == null ? null : info.getTreeConflict();
  }

  public void setPath(String path) {
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  public void setKind(boolean exists, SVNNodeKind kind) {
    myFileExists = exists;
    setKind(kind);
  }
}
