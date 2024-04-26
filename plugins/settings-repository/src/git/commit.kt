// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.diagnostic.debug
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.jetbrains.annotations.NonNls
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.icsMessage
import kotlin.coroutines.coroutineContext

suspend fun commit(repository: Repository, commitMessageFormatter: CommitMessageFormatter = IdeaCommitMessageFormatter()): Boolean {
  coroutineContext.ensureActive()

  val diff = repository.computeIndexDiff()
  val changed = reportRawProgress { reporter ->
    val monitor = JGitCoroutineProgressMonitor(currentCoroutineContext().job, reporter)
    diff.diff(monitor, ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, icsMessage("operation.progress.committing"))
  }

  // don't worry about untracked/modified only in the FS files
  if (!changed || (diff.added.isEmpty() && diff.changed.isEmpty() && diff.removed.isEmpty())) {
    if (diff.modified.isEmpty() && diff.missing.isEmpty()) {
      LOG.debug("Nothing to commit")
      return false
    }

    var edits: MutableList<PathEdit>? = null
    for (path in diff.modified) {
      if (edits == null) {
        edits = SmartList()
      }
      edits.add(AddFile(path))
    }

    for (path in diff.missing) {
      if (edits == null) {
        edits = SmartList()
      }
      edits.add(DeleteFile(path))
    }

    if (edits != null) {
      repository.edit(edits)
    }
  }

  LOG.debug { indexDiffToString(diff) }

  coroutineContext.ensureActive()

  val builder = commitMessageFormatter.prependMessage()

  // we use Github (edit via web UI) terms here
  builder.appendCompactList("Update", diff.changed)
  builder.appendCompactList("Create", diff.added)
  builder.appendCompactList("Delete", diff.removed)

  repository.commit(builder.toString())
  return true
}

@NonNls
private fun indexDiffToString(diff: IndexDiff): String {
  val builder = StringBuilder()
  builder.append("To commit:")
  builder.addList("Added", diff.added)
  builder.addList("Changed", diff.changed)
  builder.addList("Deleted", diff.removed)
  builder.addList("Modified on disk relative to the index", diff.modified)
  builder.addList("Untracked files", diff.untracked)
  builder.addList("Untracked folders", diff.untrackedFolders)
  builder.addList("Missing", diff.missing)
  return builder.toString()
}

private fun StringBuilder.appendCompactList(@NonNls name: String, list: Collection<String>) {
  addList(name, list, true)
}

private fun StringBuilder.addList(name: String, list: Collection<String>, compact: Boolean = false) {
  if (list.isEmpty()) {
    return
  }

  if (compact) {
    if (length != 0 && this[length - 1] != ' ') {
      append('\t')
    }
    append(name)
  }
  else {
    append('\t').append(name).append(':')
  }
  append(' ')

  var isNotFirst = false
  for (path in list) {
    if (isNotFirst) {
      append(',').append(' ')
    }
    else {
      isNotFirst = true
    }
    append(if (compact) PathUtilRt.getFileName(path) else path)
  }
}
