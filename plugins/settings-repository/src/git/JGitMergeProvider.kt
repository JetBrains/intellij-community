package git

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.ObjectId
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vcs.merge.MergeData
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.ObjectReader
import org.jetbrains.settingsRepository.git.getCachedBytes

class JGitMergeProvider(private val repository: Repository, private val myCommit: ObjectId, private val theirsCommit: ObjectId) : MergeProvider {
  private var objectReader: ObjectReader? = null

  override fun conflictResolvedForFile(file: VirtualFile) {
  }

  override fun isBinary(file: VirtualFile) = file.getFileType().isBinary()

  override fun loadRevisions(file: VirtualFile): MergeData {
    val mergeData = MergeData()
    val dirCache = repository.lockDirCache()
    try {
      if (objectReader == null) {
        objectReader = repository.newObjectReader()
      }

      var index = dirCache.findEntry(file.getPath())
      assert(index >= 0)

      var dirCacheEntry = dirCache.getEntry(index)
      if (dirCacheEntry.getStage() == DirCacheEntry.STAGE_2) {
        // our merge doesn't have base
        // todo ask Kirill - is it correct?
        val data = getCachedBytes(dirCacheEntry)
        mergeData.ORIGINAL = data
        mergeData.CURRENT = data
      }
      else {
        assert(dirCacheEntry.getStage() == DirCacheEntry.STAGE_1)
        mergeData.ORIGINAL = getCachedBytes(dirCacheEntry)

        dirCacheEntry = dirCache.getEntry(++index)
        assert(dirCacheEntry.getStage() == DirCacheEntry.STAGE_2)
        mergeData.CURRENT = getCachedBytes(dirCacheEntry)
      }

      dirCacheEntry = dirCache.getEntry(index + 1)
      assert(dirCacheEntry.getStage() == DirCacheEntry.STAGE_3)
      mergeData.LAST = getCachedBytes(dirCacheEntry)
    }
    finally {
      try {
        dirCache.unlock()
      }
      finally {
        if (objectReader != null) {
          objectReader!!.release()
        }
      }
    }
    return mergeData
  }

  private fun getCachedBytes(dirCacheEntry: DirCacheEntry) = objectReader!!.getCachedBytes(dirCacheEntry)
}