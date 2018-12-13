// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.openapi.util.Getter
import com.intellij.openapi.vcs.FileStatus
import org.jetbrains.idea.svn.SvnFileStatus
import org.jetbrains.idea.svn.api.BaseNodeDescription
import org.jetbrains.idea.svn.api.NodeKind
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.info.Info
import org.jetbrains.idea.svn.lock.Lock
import org.jetbrains.idea.svn.status.StatusType.*
import java.io.File

class Status private constructor(builder: Builder) : BaseNodeDescription(builder.nodeKind) {
  private val infoProvider = builder.infoProvider
  val info by lazy { if (itemStatus != STATUS_NONE) infoProvider.get() else null }

  val url = builder.url
    get() = field ?: info?.url

  private val fileExists = builder.fileExists
  override val nodeKind get() = if (fileExists) super.nodeKind else info?.nodeKind ?: super.nodeKind

  val revision: Revision = builder.revision
    get() {
      return if (field.isValid || `is`(STATUS_NONE, STATUS_UNVERSIONED, STATUS_ADDED)) field else info?.revision ?: field
    }

  val copyFromUrl get() = if (isCopied) info?.copyFromUrl else null
  val treeConflict get() = if (isTreeConflicted) info?.treeConflict else null
  val repositoryRootUrl get() = info?.repositoryRootUrl

  val file = builder.file
  val commitInfo: CommitInfo = builder.commitInfo?.build() ?: CommitInfo.EMPTY
  val itemStatus = builder.itemStatus
  val propertyStatus = builder.propertyStatus
  val remoteItemStatus = builder.remoteItemStatus
  val remotePropertyStatus = builder.remotePropertyStatus
  val isWorkingCopyLocked = builder.isWorkingCopyLocked
  val isCopied = builder.isCopied
  val isSwitched = builder.isSwitched
  val remoteLock = builder.remoteLock?.build()
  val localLock = builder.localLock?.build()
  val changeListName = builder.changeListName
  val isTreeConflicted = builder.isTreeConflicted

  fun `is`(vararg types: StatusType) = itemStatus in types
  fun isProperty(vararg types: StatusType) = propertyStatus in types

  class Builder(var file: File) {
    var infoProvider = Getter<Info?> { null }

    var url: Url? = null
    var fileExists = false
    var nodeKind = NodeKind.UNKNOWN
    var revision = Revision.UNDEFINED

    var commitInfo: CommitInfo.Builder? = null
    var itemStatus = STATUS_NONE
    var propertyStatus = STATUS_NONE
    var remoteItemStatus: StatusType? = null
    var remotePropertyStatus: StatusType? = null
    var isWorkingCopyLocked = false
    var isCopied = false
    var isSwitched = false
    var remoteLock: Lock.Builder? = null
    var localLock: Lock.Builder? = null
    var changeListName: String? = null
    var isTreeConflicted = false

    fun build() = Status(this)
  }

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
