package git

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.ObjectId
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vcs.merge.MergeData
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.ObjectReader
import org.jetbrains.settingsRepository.git.getCachedBytes
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.jgit.dirCache.AddLoadedFile
import org.jetbrains.settingsRepository.RepositoryVirtualFile
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.CharsetToolkit
import java.nio.CharBuffer

class JGitMergeProvider(private val repository: Repository, private val myCommit: ObjectId, private val theirsCommit: ObjectId) : MergeProvider {
  private var objectReader: ObjectReader? = null

  override fun conflictResolvedForFile(file: VirtualFile) {
    // we can postpone dir cache update (on merge dialog close) to reduce number of flush, but it can leads to data loss (if app crashed during merge - nothing will be saved)
    val virtualFile = file as RepositoryVirtualFile
    // update dir cache
    var bytes = virtualFile.content
    val size: Int
    // not null if user accepts some revision (virtual file will be directly modified), otherwise document will be modified
    if (bytes == null) {
      val chars = FileDocumentManager.getInstance().getCachedDocument(virtualFile)!!.getImmutableCharSequence()
      val byteBuffer = CharsetToolkit.UTF8_CHARSET.encode(CharBuffer.wrap(chars))
      bytes = byteBuffer.array()
      size = byteBuffer.remaining()
    }
    else {
      size = bytes!!.size
    }
    repository.edit(AddLoadedFile(virtualFile.getPath(), bytes!!, size, System.currentTimeMillis()))
    // update working directory file
    FileUtil.writeToFile(File(repository.getWorkTree(), virtualFile.getPath()), bytes!!, 0, size)
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