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
package org.jetbrains.idea.svn17;

import com.intellij.openapi.vcs.FileStatus;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SvnStatusConvertor {
  private SvnStatusConvertor() {
  }

  public static FileStatus convertStatus(final SVNStatus status) throws SVNException {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (SvnVcs17.svnStatusIsUnversioned(status)) {
      return FileStatus.UNKNOWN;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_MISSING)) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_EXTERNAL)) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_OBSTRUCTED)) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_IGNORED)) {
      return FileStatus.IGNORED;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_ADDED)) {
      return FileStatus.ADDED;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_DELETED)) {
      return FileStatus.DELETED;
    }
    else if (SvnVcs17.svnStatusIs(status, SVNStatusType.STATUS_REPLACED)) {
      return SvnFileStatus.REPLACED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
      if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED &&
        status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
        return FileStatus.MERGED_WITH_BOTH_CONFLICTS;
      } else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
        return FileStatus.MERGED_WITH_CONFLICTS;
      }
      return FileStatus.MERGED_WITH_PROPERTY_CONFLICTS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
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
}
