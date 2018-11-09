// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info

import com.intellij.util.ObjectUtils.notNull
import org.jetbrains.idea.svn.api.*
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.conflict.TreeConflictDescription
import org.jetbrains.idea.svn.lock.Lock
import java.io.File

private fun resolveConflictFile(file: File?, path: String?) = if (file != null && path != null) File(file.parentFile, path) else null

class Info : BaseNodeDescription {
  val file: File?
  val url: Url?
  val revision: Revision
  val repositoryRootURL: Url?
  val repositoryUUID: String?
  val commitInfo: CommitInfo
  val lock: Lock?
  val schedule: String?
  val copyFromURL: Url?
  val copyFromRevision: Revision
  val conflictOldFile: File?
  val conflictNewFile: File?
  val conflictWrkFile: File?
  val depth: Depth?
  val treeConflict: TreeConflictDescription?
  val kind get() = myKind

  @Deprecated("Use url property", ReplaceWith("url"))
  fun getURL() = url

  constructor(file: File?,
              url: Url?,
              rootURL: Url?,
              revision: Long,
              kind: NodeKind,
              uuid: String?,
              commitInfo: CommitInfo?,
              schedule: String?,
              copyFromURL: Url?,
              copyFromRevision: Long,
              conflictOldFileName: String?,
              conflictNewFileName: String?,
              conflictWorkingFileName: String?,
              lock: Lock?,
              depth: Depth?,
              treeConflict: TreeConflictDescription?) : super(kind) {
    this.file = file
    this.url = url
    this.revision = Revision.of(revision)
    repositoryUUID = uuid
    repositoryRootURL = rootURL

    this.commitInfo = notNull(commitInfo, CommitInfo.EMPTY)
    this.schedule = schedule

    this.copyFromURL = copyFromURL
    this.copyFromRevision = Revision.of(copyFromRevision)

    this.lock = lock
    this.treeConflict = treeConflict

    conflictOldFile = resolveConflictFile(file, conflictOldFileName)
    conflictNewFile = resolveConflictFile(file, conflictNewFileName)
    conflictWrkFile = resolveConflictFile(file, conflictWorkingFileName)

    this.depth = depth
  }

  constructor(url: Url,
              revision: Revision,
              kind: NodeKind,
              uuid: String,
              reposRootURL: Url,
              commitInfo: CommitInfo?,
              lock: Lock?,
              depth: Depth) : super(kind) {
    this.url = url
    this.revision = revision
    repositoryRootURL = reposRootURL
    repositoryUUID = uuid

    this.commitInfo = notNull(commitInfo, CommitInfo.EMPTY)
    this.lock = lock
    this.depth = depth

    file = null
    schedule = null
    copyFromURL = null
    copyFromRevision = Revision.UNDEFINED
    conflictOldFile = null
    conflictNewFile = null
    conflictWrkFile = null
    treeConflict = null
  }

  companion object {
    const val SCHEDULE_ADD = "add"
  }
}