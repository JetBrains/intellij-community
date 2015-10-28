/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.PathEdit
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.PROJECTS_DIR_NAME

fun commit(repository: Repository, indicator: ProgressIndicator?, commitMessageFormatter: CommitMessageFormatter = IdeaCommitMessageFormatter()): Boolean {
  indicator?.checkCanceled()

  val diff = repository.computeIndexDiff()
  val changed = diff.diff(indicator?.asProgressMonitor(), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Commit")

  // don't worry about untracked/modified only in the FS files
  if (!changed || (diff.added.isEmpty() && diff.changed.isEmpty() && diff.removed.isEmpty())) {
    if (diff.modified.isEmpty()) {
      LOG.debug("Nothing to commit")
      return false
    }

    var edits: MutableList<PathEdit>? = null
    for (path in diff.modified) {
      if (!path.startsWith(PROJECTS_DIR_NAME)) {
        if (edits == null) {
          edits = SmartList()
        }
        edits.add(AddFile(path))
      }
    }
    if (edits != null) {
      repository.edit(edits)
    }
  }

  LOG.debug { indexDiffToString(diff) }

  indicator?.checkCanceled()

  val builder = StringBuilder()
  commitMessageFormatter.prependMessage(builder)

  // we use Github (edit via web UI) terms here
  builder.appendCompactList("Update", diff.changed)
  builder.appendCompactList("Create", diff.added)
  builder.appendCompactList("Delete", diff.removed)

  repository.commit(builder.toString())
  return true
}

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

private fun StringBuilder.appendCompactList(name: String, list: Collection<String>) {
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
