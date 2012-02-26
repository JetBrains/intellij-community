/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.FileStatus;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SvnStatusConvertor {
  private SvnStatusConvertor() {
  }

  public static FileStatus convertStatus(final SVNStatus status) throws SVNException {
    return convertStatus(status, true);
  }

  public static FileStatus convertStatus(final SVNStatus status, final boolean noticeProperties) throws SVNException {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (SvnVcs.svnStatusIsUnversioned(status)) {
      return FileStatus.UNKNOWN;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_MISSING)) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_EXTERNAL)) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_OBSTRUCTED)) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_IGNORED)) {
      return FileStatus.IGNORED;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_ADDED)) {
      return FileStatus.ADDED;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_DELETED)) {
      return FileStatus.DELETED;
    }
    else if (SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_REPLACED)) {
      return SvnFileStatus.REPLACED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
             noticeProperties && status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
      if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED &&
          noticeProperties && status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
        return FileStatus.MERGED_WITH_BOTH_CONFLICTS;
      } else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
        return FileStatus.MERGED_WITH_CONFLICTS;
      }
      return FileStatus.MERGED_WITH_PROPERTY_CONFLICTS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
             noticeProperties && status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
      return FileStatus.MODIFIED;
    }
    else if (status.isSwitched()) {
      return FileStatus.SWITCHED;
    }
    else if (status.isCopied()) {
      return FileStatus.ADDED;
    }
    return FileStatus.NOT_CHANGED;
  }

  public static FileStatus convertPropertyStatus(final SVNStatusType status) throws SVNException {
    return convertSingleStatus(status, FileStatus.MERGED_WITH_PROPERTY_CONFLICTS);
  }

  public static FileStatus convertContentsStatus(final SVNStatus status) throws SVNException {
    return convertStatus(status, false);
  }

  private static FileStatus convertSingleStatus(final SVNStatusType status, final FileStatus defaultConflictStatus) throws SVNException {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (SVNStatusType.STATUS_UNVERSIONED.equals(status)) {
      return FileStatus.UNKNOWN;
    }
    else if (SVNStatusType.STATUS_MISSING.equals(status)) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (SVNStatusType.STATUS_EXTERNAL.equals(status)) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (SVNStatusType.STATUS_OBSTRUCTED.equals(status)) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (SVNStatusType.STATUS_IGNORED.equals(status)) {
      return FileStatus.IGNORED;
    }
    else if (SVNStatusType.STATUS_ADDED.equals(status)) {
      return FileStatus.ADDED;
    }
    else if (SVNStatusType.STATUS_DELETED.equals(status)) {
      return FileStatus.DELETED;
    }
    else if (SVNStatusType.STATUS_REPLACED.equals(status)) {
      return SvnFileStatus.REPLACED;
    }
    else if (status == SVNStatusType.STATUS_CONFLICTED) {
      return defaultConflictStatus;
    }
    else if (status == SVNStatusType.STATUS_MODIFIED) {
      return FileStatus.MODIFIED;
    }
    return FileStatus.NOT_CHANGED;
  }
}
