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
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import org.jetbrains.plugins.settingsRepository.removeFileAndParentDirectoryIfEmpty

class Reset(manager: GitRepositoryManager, indicator: ProgressIndicator) : Pull(manager, indicator) {
  fun reset() {
    LOG.debug("Reset to theirs")

    // todo commit only after diff
    // we do merge before - grab added/deleted/renamed/modified files
    pull(MergeStrategy.THEIRS)

    // and then we need to remove all files missing in remote
    val fetchRefSpecs = remoteConfig.getFetchRefSpecs()
    assert(fetchRefSpecs.size == 1)

    val reader = repository.newObjectReader()

    fun prepareTreeParser(ref: String): AbstractTreeIterator {
      val treeParser = CanonicalTreeParser()
      treeParser.reset(reader, repository.resolve(ref + "^{tree}")!!)
      return treeParser;
    }

    try {
      val myTreeIterator = prepareTreeParser(Constants.HEAD)
      val theirsTreeIterator = prepareTreeParser(fetchRefSpecs[0].getDestination()!!)

      val walk = TreeWalk(reader)
      walk.addTree(myTreeIterator)
      walk.addTree(theirsTreeIterator)
      walk.setRecursive(true)

      val edits = computeEdits(walk)
      if (!edits.isEmpty()) {
        repository.edit(edits)
      }
    }
    finally {
      reader.release()
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