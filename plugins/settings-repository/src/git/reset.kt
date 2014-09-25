package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.lib.MutableObjectId
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.eclipse.jgit.lib.FileMode
import java.util.ArrayList
import org.jetbrains.jgit.dirCache.PathEdit
import org.eclipse.jgit.merge.MergeStrategy
import org.jetbrains.jgit.dirCache.DeleteFile
import java.io.File
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.removeFileAndParentDirectoryIfEmpty
import org.jetbrains.jgit.dirCache.deleteAllFiles
import com.intellij.openapi.util.io.FileUtil
import org.eclipse.jgit.lib.Constants
import org.jetbrains.settingsRepository.UpdateResult
import org.jetbrains.settingsRepository.MutableUpdateResult

class Reset(manager: GitRepositoryManager, indicator: ProgressIndicator) : Pull(manager, indicator) {
  fun reset(toTheirs: Boolean): UpdateResult {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Reset to ${if (toTheirs) "theirs" else "my"}")
    }

    val resetResult = repository.resetHard()
    val result = MutableUpdateResult(resetResult.getUpdated().keySet(), resetResult.getRemoved())

    indicator.checkCanceled()

    val commitMessage = "Reset to ${if (toTheirs) manager.getUpstream() else "my"}"
    // grab added/deleted/renamed/modified files
    val mergeStrategy = if (toTheirs) MergeStrategy.THEIRS else MergeStrategy.OURS
    val firstMergeResult = pull(mergeStrategy, commitMessage)
    if (firstMergeResult == null) {
      // nothing to merge, so, we merge latest origin commit
      val fetchRefSpecs = remoteConfig.getFetchRefSpecs()
      assert(fetchRefSpecs.size == 1)

      val latestUpstreamCommit = repository.getRef(fetchRefSpecs[0].getDestination()!!)
      if (latestUpstreamCommit == null) {
        if (mergeStrategy == MergeStrategy.OURS) {
          LOG.debug("uninitialized remote (empty) - we don't need to merge")
        }
        else {
          // todo update UpdateResult
          repository.deleteAllFiles()

          val files = repository.getWorkTree().listFiles { it.getName() != Constants.DOT_GIT }
          if (files != null) {
            for (file in files) {
              FileUtil.delete(file)
            }
          }

          manager.commit(commitMessage)
        }
        return result
      }

      val mergeResult = merge(latestUpstreamCommit, mergeStrategy, true, forceMerge = true, commitMessage = commitMessage)
      if (!mergeResult.mergeStatus.isSuccessful()) {
        throw IllegalStateException(mergeResult.toString())
      }

      updateResult(result)
    }
    else {
      result.add(firstMergeResult)
    }
    return result

//    val reader = repository.newObjectReader()
//
//    fun prepareTreeParser(ref: String): AbstractTreeIterator {
//      val treeParser = CanonicalTreeParser()
//      treeParser.reset(reader, repository.resolve(ref + "^{tree}")!!)
//      return treeParser;
//    }

//    LOG.debug("Compute diff")
//    val edits: List<PathEdit>
//    try {
//      val myTreeIterator = prepareTreeParser(Constants.HEAD)
//      val theirsTreeIterator = prepareTreeParser(fetchRefSpecs[0].getDestination()!!)
//
//      val walk = TreeWalk(reader)
//      walk.addTree(myTreeIterator)
//      walk.addTree(theirsTreeIterator)
//      walk.setRecursive(true)
//
//      edits = computeEdits(walk)
//      if (!edits.isEmpty()) {
//        LOG.debug("Apply diff")
//        repository.edit(edits)
//      }
//    }
//    finally {
//      reader.release()
//    }
//
//    if (edits.isEmpty() && when (mergeResult?.getMergeStatus()) {
//      MergeStatus.FAST_FORWARD, MergeStatus.ALREADY_UP_TO_DATE, null -> true
//      else -> false
//    }) {
//      return
//    }

//    manager.createCommitCommand().
  }

  private fun updateResult(result: MutableUpdateResult) {
    if (mergeUpdateResult != null) {
      result.add(mergeUpdateResult!!)
      mergeUpdateResult = null
    }
  }

  private fun computeEdits(walk: TreeWalk): List<PathEdit> {
    val edits = ArrayList<PathEdit>()
    val idBuf = MutableObjectId()
    val workingDirectory = repository.getWorkTree()
    while (walk.next()) {
      val newMode = walk.getFileMode(1)
      if (newMode == FileMode.MISSING) {
        edits.add(DeleteFile(walk.getRawPath()))
        removeFileAndParentDirectoryIfEmpty(File(workingDirectory, walk.getPathString()), workingDirectory)
        continue
      }

      val oldMode = walk.getFileMode(0)
      if (oldMode == FileMode.MISSING) {
        LOG.warn("${walk.getPathString()} missing, but it is illegal state - we did fetch&merge before")
        continue
      }

      walk.getObjectId(idBuf, 0)
      val oldId = AbbreviatedObjectId.fromObjectId(idBuf)

      walk.getObjectId(idBuf, 1)
      val newId = AbbreviatedObjectId.fromObjectId(idBuf)

      if (oldId != newId || oldMode != newMode) {
        LOG.warn("${walk.getPathString()} modified, but it is illegal state - we did fetch&merge before")
      }
    }
    return edits
  }
}