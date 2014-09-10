package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.ProgressMonitor
import org.jetbrains.plugins.settingsRepository.IcsUrlBuilder
import org.jetbrains.plugins.settingsRepository.LOG
import java.util.ArrayList
import org.jetbrains.jgit.dirCache.PathEdit

fun commit(manager: GitRepositoryManager, indicator: ProgressIndicator) {
  val index = manager.repository.computeIndexDiff()
  val changed = index.diff(JGitProgressMonitor(indicator), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Commit")

  if (LOG.isDebugEnabled()) {
    LOG.debug("Commit")
    LOG.debug(indexDiffToString(index))
  }

  // don't worry about untracked/modified only in the FS files
  if (!changed || (index.getAdded().isEmpty() && index.getChanged().isEmpty() && index.getRemoved().isEmpty())) {
    if (index.getModified().isEmpty()) {
      LOG.debug("Skip scheduled commit, nothing to commit")
      return
    }

    var edits: MutableList<PathEdit>? = null
    for (path in index.getModified()) {
      if (!path.startsWith(IcsUrlBuilder.PROJECTS_DIR_NAME)) {
        if (edits == null) {
          edits = ArrayList()
        }
        edits!!.add(AddFile(path))
      }
    }
    if (edits != null) {
      manager.repository.edit(edits!!)
    }
  }

  manager.createCommitCommand().setMessage("").call()
}

private fun indexDiffToString(diff: IndexDiff): String {
  val builder = StringBuilder()
  builder.append("To commit:")
  addList("Added", diff.getAdded(), builder)
  addList("Changed", diff.getChanged(), builder)
  addList("Removed", diff.getRemoved(), builder)
  addList("Modified on disk relative to the index", diff.getModified(), builder)
  addList("Untracked files", diff.getUntracked(), builder)
  addList("Untracked folders", diff.getUntrackedFolders(), builder)
  addList("Missing", diff.getMissing(), builder)
  return builder.toString()
}

private fun addList(name: String, list: Collection<String>, builder: StringBuilder) {
  if (list.isEmpty()) {
    return
  }

  builder.append('\t').append(name).append(": ")
  var isNotFirst = false
  for (path in list) {
    if (isNotFirst) {
      builder.append(',').append(' ')
    }
    else {
      isNotFirst = true
    }
    builder.append(path)
  }
}
