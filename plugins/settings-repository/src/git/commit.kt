package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.ProgressMonitor
import org.jetbrains.jgit.dirCache.PathEdit
import com.intellij.util.SmartList
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.PROJECTS_DIR_NAME
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.jgit.dirCache.AddFile
import com.intellij.util.PathUtilRt

fun commit(manager: GitRepositoryManager, indicator: ProgressIndicator) {
  indicator.checkCanceled()

  val diff = manager.repository.computeIndexDiff()
  val changed = diff.diff(JGitProgressMonitor(indicator), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Commit")

  if (LOG.isDebugEnabled()) {
    LOG.debug("Commit")
    LOG.debug(indexDiffToString(diff))
  }

  // don't worry about untracked/modified only in the FS files
  if (!changed || (diff.getAdded().isEmpty() && diff.getChanged().isEmpty() && diff.getRemoved().isEmpty())) {
    if (diff.getModified().isEmpty()) {
      LOG.debug("Skip scheduled commit, nothing to commit")
      return
    }

    var edits: MutableList<PathEdit>? = null
    for (path in diff.getModified()) {
      if (!path.startsWith(PROJECTS_DIR_NAME)) {
        if (edits == null) {
          edits = SmartList()
        }
        edits!!.add(AddFile(path))
      }
    }
    if (edits != null) {
      manager.repository.edit(edits!!)
    }
  }

  indicator.checkCanceled()

  val builder = StringBuilder()
  // we use Github (edit via web UI) terms here
  addCompactList("Update", diff.getChanged(), builder)
  addCompactList("Create", diff.getAdded(), builder)
  addCompactList("Delete", diff.getRemoved(), builder)

  manager.commit(builder.toString())
}

private fun indexDiffToString(diff: IndexDiff): String {
  val builder = StringBuilder()
  builder.append("To commit:")
  addList("Added", diff.getAdded(), builder)
  addList("Changed", diff.getChanged(), builder)
  addList("Deleted", diff.getRemoved(), builder)
  addList("Modified on disk relative to the index", diff.getModified(), builder)
  addList("Untracked files", diff.getUntracked(), builder)
  addList("Untracked folders", diff.getUntrackedFolders(), builder)
  addList("Missing", diff.getMissing(), builder)
  return builder.toString()
}

private fun addCompactList(name: String, list: Collection<String>, builder: StringBuilder) {
  addList(name, list, builder, true)
}

private fun addList(name: String, list: Collection<String>, builder: StringBuilder, compact: Boolean = false) {
  if (list.isEmpty()) {
    return
  }

  if (compact) {
    if (builder.length() != 0) {
      builder.append(' ')
    }
    builder.append(name)
  }
  else {
    builder.append('\t').append(name).append(':')
  }
  builder.append(' ')

  var isNotFirst = false
  for (path in list) {
    if (isNotFirst) {
      builder.append(',').append(' ')
    }
    else {
      isNotFirst = true
    }
    builder.append(if (compact) PathUtilRt.getFileName(path) else path)
  }
}
