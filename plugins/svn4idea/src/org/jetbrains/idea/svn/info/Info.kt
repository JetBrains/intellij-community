// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info

import org.jetbrains.idea.svn.api.*
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.conflict.TreeConflictDescription
import org.jetbrains.idea.svn.lock.Lock
import java.io.File

private fun resolveConflictFile(file: File?, path: String?) = if (file != null && path != null) File(file.parentFile, path) else null

class Info(val file: File?,
           val url: Url?,
           val revision: Revision,
           kind: NodeKind,
           val repositoryRootUrl: Url?,
           val repositoryId: String?,
           commitInfo: CommitInfo? = null,
           val schedule: String? = null,
           val depth: Depth? = null,
           val copyFromUrl: Url? = null,
           val copyFromRevision: Revision = Revision.UNDEFINED,
           val lock: Lock? = null,
           conflictOldFileName: String? = null,
           conflictNewFileName: String? = null,
           conflictWorkingFileName: String? = null,
           val treeConflict: TreeConflictDescription? = null) : BaseNodeDescription(kind) {
  val commitInfo: CommitInfo = commitInfo ?: CommitInfo.EMPTY
  val conflictOldFile = resolveConflictFile(file, conflictOldFileName)
  val conflictNewFile = resolveConflictFile(file, conflictNewFileName)
  val conflictWrkFile = resolveConflictFile(file, conflictWorkingFileName)
  val kind get() = myKind

  @Deprecated("Use url property", ReplaceWith("url"))
  fun getURL() = url

  companion object {
    const val SCHEDULE_ADD = "add"
  }
}