// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.openapi.util.Getter
import com.intellij.openapi.vcs.FileStatus
import org.jetbrains.idea.svn.SvnFileStatus
import org.jetbrains.idea.svn.api.NodeKind
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.info.Info
import org.jetbrains.idea.svn.lock.Lock
import org.jetbrains.idea.svn.status.StatusType.*
import java.io.File

/**
 * TODO: Could also inherit BaseNodeDescription when myNodeKind becomes final.
 */
class Status {
  var infoProvider = Getter<Info?> { null }
  val info by lazy { if (itemStatus != STATUS_NONE) infoProvider.get() else null }

  var url: Url? = null
    get() = field ?: info?.url

  var file: File? = null
    get() = field ?: info?.file

  private var myFileExists = false

  var nodeKind = NodeKind.UNKNOWN
    get() = if (myFileExists) field else info?.nodeKind ?: field

  fun setNodeKind(exists: Boolean, nodeKind: NodeKind) {
    myFileExists = exists
    this.nodeKind = nodeKind
  }

  var revision = Revision.UNDEFINED
    get() {
      val revision = field

      return if (revision.isValid || `is`(STATUS_NONE, STATUS_UNVERSIONED, STATUS_ADDED)) revision
      else info?.revision ?: revision
    }

  val copyFromUrl get() = if (isCopied) info?.copyFromUrl else null
  val treeConflict get() = if (isTreeConflicted) info?.treeConflict else null
  val repositoryRootUrl get() = info?.repositoryRootUrl

  var committedRevision = Revision.UNDEFINED
  var itemStatus = STATUS_NONE
  var propertyStatus = STATUS_NONE
  var remoteItemStatus: StatusType? = null
  var remotePropertyStatus: StatusType? = null
  var isWorkingCopyLocked = false
  var isCopied = false
  var isSwitched = false
  var remoteLock: Lock? = null
  var localLock: Lock? = null
  var changeListName: String? = null
  var isTreeConflicted = false

  fun `is`(vararg types: StatusType) = itemStatus in types
  fun isProperty(vararg types: StatusType) = propertyStatus in types

  companion object {
    @JvmStatic
    fun convertStatus(status: Status) = convertStatus(status.itemStatus, status.propertyStatus, status.isSwitched, status.isCopied)

    @JvmStatic
    fun convertStatus(itemStatus: StatusType?, propertyStatus: StatusType?, isSwitched: Boolean, isCopied: Boolean): FileStatus =
      when {
        itemStatus == null -> FileStatus.UNKNOWN
        STATUS_UNVERSIONED == itemStatus -> FileStatus.UNKNOWN
        STATUS_MISSING == itemStatus -> FileStatus.DELETED_FROM_FS
        STATUS_EXTERNAL == itemStatus -> SvnFileStatus.EXTERNAL
        STATUS_OBSTRUCTED == itemStatus -> SvnFileStatus.OBSTRUCTED
        STATUS_IGNORED == itemStatus -> FileStatus.IGNORED
        STATUS_ADDED == itemStatus -> FileStatus.ADDED
        STATUS_DELETED == itemStatus -> FileStatus.DELETED
        STATUS_REPLACED == itemStatus -> SvnFileStatus.REPLACED
        STATUS_CONFLICTED == itemStatus || STATUS_CONFLICTED == propertyStatus -> {
          if (STATUS_CONFLICTED == itemStatus && STATUS_CONFLICTED == propertyStatus) {
            FileStatus.MERGED_WITH_BOTH_CONFLICTS
          }
          else if (STATUS_CONFLICTED == itemStatus) {
            FileStatus.MERGED_WITH_CONFLICTS
          }
          FileStatus.MERGED_WITH_PROPERTY_CONFLICTS
        }
        STATUS_MODIFIED == itemStatus || STATUS_MODIFIED == propertyStatus -> FileStatus.MODIFIED
        isSwitched -> FileStatus.SWITCHED
        isCopied -> FileStatus.ADDED
        else -> FileStatus.NOT_CHANGED
      }

    @JvmStatic
    fun convertPropertyStatus(status: StatusType?): FileStatus = when (status) {
      null -> FileStatus.UNKNOWN
      STATUS_UNVERSIONED -> FileStatus.UNKNOWN
      STATUS_MISSING -> FileStatus.DELETED_FROM_FS
      STATUS_EXTERNAL -> SvnFileStatus.EXTERNAL
      STATUS_OBSTRUCTED -> SvnFileStatus.OBSTRUCTED
      STATUS_IGNORED -> FileStatus.IGNORED
      STATUS_ADDED -> FileStatus.ADDED
      STATUS_DELETED -> FileStatus.DELETED
      STATUS_REPLACED -> SvnFileStatus.REPLACED
      STATUS_CONFLICTED -> FileStatus.MERGED_WITH_PROPERTY_CONFLICTS
      STATUS_MODIFIED -> FileStatus.MODIFIED
      else -> FileStatus.NOT_CHANGED
    }
  }
}
