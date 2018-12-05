// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

public class SvnStatusConvertor {
  private SvnStatusConvertor() {
  }

  @NotNull
  public static FileStatus convertStatus(@Nullable StatusType itemStatus, @Nullable StatusType propertiesStatus) {
    Status status = new Status();

    status.setContentsStatus(itemStatus);
    status.setPropertiesStatus(propertiesStatus);

    return convertStatus(status);
  }

  @NotNull
  public static FileStatus convertStatus(@Nullable final Status status) {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (status.is(StatusType.STATUS_UNVERSIONED)) {
      return FileStatus.UNKNOWN;
    }
    else if (status.is(StatusType.STATUS_MISSING)) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (status.is(StatusType.STATUS_EXTERNAL)) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (status.is(StatusType.STATUS_OBSTRUCTED)) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (status.is(StatusType.STATUS_IGNORED)) {
      return FileStatus.IGNORED;
    }
    else if (status.is(StatusType.STATUS_ADDED)) {
      return FileStatus.ADDED;
    }
    else if (status.is(StatusType.STATUS_DELETED)) {
      return FileStatus.DELETED;
    }
    else if (status.is(StatusType.STATUS_REPLACED)) {
      return SvnFileStatus.REPLACED;
    }
    else if (status.is(StatusType.STATUS_CONFLICTED) || status.isProperty(StatusType.STATUS_CONFLICTED)) {
      if (status.is(StatusType.STATUS_CONFLICTED) && status.isProperty(StatusType.STATUS_CONFLICTED)) {
        return FileStatus.MERGED_WITH_BOTH_CONFLICTS;
      }
      else if (status.is(StatusType.STATUS_CONFLICTED)) {
        return FileStatus.MERGED_WITH_CONFLICTS;
      }
      return FileStatus.MERGED_WITH_PROPERTY_CONFLICTS;
    }
    else if (status.is(StatusType.STATUS_MODIFIED) || status.isProperty(StatusType.STATUS_MODIFIED)) {
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

  @NotNull
  public static FileStatus convertPropertyStatus(final StatusType status) {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (StatusType.STATUS_UNVERSIONED.equals(status)) {
      return FileStatus.UNKNOWN;
    }
    else if (StatusType.STATUS_MISSING.equals(status)) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (StatusType.STATUS_EXTERNAL.equals(status)) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (StatusType.STATUS_OBSTRUCTED.equals(status)) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (StatusType.STATUS_IGNORED.equals(status)) {
      return FileStatus.IGNORED;
    }
    else if (StatusType.STATUS_ADDED.equals(status)) {
      return FileStatus.ADDED;
    }
    else if (StatusType.STATUS_DELETED.equals(status)) {
      return FileStatus.DELETED;
    }
    else if (StatusType.STATUS_REPLACED.equals(status)) {
      return SvnFileStatus.REPLACED;
    }
    else if (status == StatusType.STATUS_CONFLICTED) {
      return FileStatus.MERGED_WITH_PROPERTY_CONFLICTS;
    }
    else if (status == StatusType.STATUS_MODIFIED) {
      return FileStatus.MODIFIED;
    }
    return FileStatus.NOT_CHANGED;
  }
}
