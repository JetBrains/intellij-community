package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.settingsRepository.LOG
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.lib.MutableObjectId
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.eclipse.jgit.lib.FileMode
import java.util.ArrayList
import org.jetbrains.jgit.dirCache.PathEdit
import org.eclipse.jgit.merge.MergeStrategy
import org.jetbrains.jgit.dirCache.DeleteFile
import java.io.File
import org.jetbrains.plugins.settingsRepository.removeFileAndParentDirectoryIfEmpty
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.api.MergeResult.MergeStatus
import org.eclipse.jgit.api.ResetCommand

class Reset(manager: GitRepositoryManager, indicator: ProgressIndicator) : Pull(manager, indicator) {
  fun reset() {
    LOG.debug("Reset to theirs")

    LOG.debug("Reset working directory")
    manager.git.reset().setMode(ResetCommand.ResetType.HARD).call()

    val commitMessage = "Reset to " + manager.getUpstream()
    // grab added/deleted/renamed/modified files
    var mergeResult = pull(MergeStrategy.THEIRS, commitMessage)
    if (mergeResult == null) {
      // nothing to merge, so, we merge latest origin commit
      val fetchRefSpecs = remoteConfig.getFetchRefSpecs()
      assert(fetchRefSpecs.size == 1)

      mergeResult = merge(repository.getRef(fetchRefSpecs[0].getDestination()!!)!!, MergeStrategy.THEIRS, true, forceMerge = true, commitMessage = commitMessage)
      if (mergeResult?.getMergeStatus()?.isSuccessful() !== true) {
        throw IllegalStateException(mergeResult.toString())
      }
    }

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

  private fun computeEdits(walk: TreeWalk): List<PathEdit> {
    val edits = ArrayList<PathEdit>()
    val idBuf = MutableObjectId()
    val workingDirectory = repository.getWorkTree()
    while (walk.next()) {
      val newMode = walk.getFileMode(1)
      if (newMode == FileMode.MISSING) {
        edits.add(DeleteFile(walk.getRawPath()))
        removeFileAndParentDirectoryIfEmpty(File(workingDirectory, walk.getPathString()), true, workingDirectory)
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