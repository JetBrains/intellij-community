package org.jetbrains.settingsRepository.git

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.ProgressMonitor
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.PathEdit
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.PROJECTS_DIR_NAME
import java.net.InetAddress

fun commit(manager: GitRepositoryManager, indicator: ProgressIndicator): Boolean {
  indicator.checkCanceled()

  val diff = manager.repository.computeIndexDiff()
  val changed = diff.diff(indicator.asProgressMonitor(), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Commit")

  // don't worry about untracked/modified only in the FS files
  if (!changed || (diff.getAdded().isEmpty() && diff.getChanged().isEmpty() && diff.getRemoved().isEmpty())) {
    if (diff.getModified().isEmpty()) {
      LOG.debug("Nothing to commit")
      return false
    }

    var edits: MutableList<PathEdit>? = null
    for (path in diff.getModified()) {
      if (!path.startsWith(PROJECTS_DIR_NAME)) {
        if (edits == null) {
          edits = SmartList()
        }
        edits.add(AddFile(path))
      }
    }
    if (edits != null) {
      manager.repository.edit(edits)
    }
  }

  if (LOG.isDebugEnabled()) {
    LOG.debug(indexDiffToString(diff))
  }

  indicator.checkCanceled()

  val builder = StringBuilder()
  builder.append(ApplicationInfoEx.getInstanceEx()!!.getFullApplicationName())
  builder.append(' ' ).append('<').append(System.getProperty("user.name", "unknown-user")).append('@').append(InetAddress.getLocalHost().getHostName())
  builder.append(' ')

  // we use Github (edit via web UI) terms here
  addCompactList("Update", diff.getChanged(), builder)
  addCompactList("Create", diff.getAdded(), builder)
  addCompactList("Delete", diff.getRemoved(), builder)

  manager.commit(builder.toString())
  return true
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
    if (builder.length() != 0 && builder.charAt(builder.length() - 1) != ' ') {
      builder.append('\t')
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
