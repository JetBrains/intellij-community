// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info

import com.intellij.openapi.util.io.FileUtil.isAbsolute
import org.jetbrains.idea.svn.api.*
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.conflict.TreeConflictDescription
import org.jetbrains.idea.svn.lock.Lock
import java.io.File

private fun resolveConflictFile(file: File?, path: String?): File? {
  if (path == null) return null
  if (isAbsolute(path)) return File(path)

  val parent = file?.parentFile ?: throw IllegalArgumentException("Could not resolve conflict file for $file and $path")
  return File(parent, path)
}

class Info(val file: File?,
           val url: Url?,
           val revision: Revision,
           nodeKind: NodeKind,
           val repositoryRootUrl: Url?,
           val repositoryId: String?,
           commitInfo: CommitInfo? = null,
           val schedule: String? = null,
           val depth: Depth? = null,
           val copyFromUrl: Url? = null,
           val copyFromRevision: Revision = Revision.UNDEFINED,
           val lock: Lock? = null,
           conflictOldFilePath: String? = null,
           conflictNewFilePath: String? = null,
           conflictWorkingFilePath: String? = null,
           val treeConflict: TreeConflictDescription? = null) : BaseNodeDescription(nodeKind) {
  val commitInfo: CommitInfo = commitInfo ?: CommitInfo.EMPTY
  val conflictOldFile = resolveConflictFile(file, conflictOldFilePath)
  val conflictNewFile = resolveConflictFile(file, conflictNewFilePath)
  val conflictWrkFile = resolveConflictFile(file, conflictWorkingFilePath)

  companion object {
    const val SCHEDULE_ADD = "add"
  }
}