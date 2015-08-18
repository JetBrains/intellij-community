package org.jetbrains.settingsRepository.git

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ColumnInfo
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.jetbrains.jgit.dirCache.deletePath
import org.jetbrains.jgit.dirCache.writePath
import org.jetbrains.settingsRepository.RepositoryVirtualFile
import java.nio.CharBuffer
import java.util.ArrayList

private fun conflictsToVirtualFiles(map: Map<String, org.eclipse.jgit.merge.MergeResult<*>>): MutableList<VirtualFile> {
  val result = ArrayList<VirtualFile>(map.size())
  for (path in map.keySet()) {
    result.add(RepositoryVirtualFile(path))
  }
  return result
}

class JGitMergeProvider(private val repository: Repository, private val myCommit: ObjectId, private val theirsCommit: ObjectId, private val conflicts: Map<String, org.eclipse.jgit.merge.MergeResult<*>>) : MergeProvider2 {
  override fun createMergeSession(files: List<VirtualFile>) = JGitMergeSession()

  override fun conflictResolvedForFile(file: VirtualFile) {
    // we can postpone dir cache update (on merge dialog close) to reduce number of flush, but it can leads to data loss (if app crashed during merge - nothing will be saved)
    // update dir cache
    val bytes = (file as RepositoryVirtualFile).content
    // not null if user accepts some revision (virtual file will be directly modified), otherwise document will be modified
    if (bytes == null) {
      val chars = FileDocumentManager.getInstance().getCachedDocument(file)!!.getImmutableCharSequence()
      val byteBuffer = CharsetToolkit.UTF8_CHARSET.encode(CharBuffer.wrap(chars))
      addFile(byteBuffer.array(), file, byteBuffer.remaining())
    }
    else {
      addFile(bytes, file)
    }
  }

  // cannot be private due to Kotlin bug
  fun addFile(bytes: ByteArray, file: VirtualFile, size: Int = bytes.size()) {
    repository.writePath(file.getPath(), bytes, size)
  }

  override fun isBinary(file: VirtualFile) = file.getFileType().isBinary()

  override fun loadRevisions(file: VirtualFile): MergeData {
    val sequences = conflicts[file.getPath()]!!.getSequences()
    val mergeData = MergeData()
    mergeData.ORIGINAL = (sequences[0] as RawText).getContent()
    mergeData.CURRENT = (sequences[1] as RawText).getContent()
    mergeData.LAST = (sequences[2] as RawText).getContent()
    return mergeData
  }

  private inner class JGitMergeSession : MergeSession {
    override fun getMergeInfoColumns(): Array<ColumnInfo<out Any?, out Any?>> {
      return arrayOf(StatusColumn(false), StatusColumn(true))
    }

    override fun canMerge(file: VirtualFile) = conflicts.contains(file.getPath())

    override fun conflictResolvedForFile(file: VirtualFile, resolution: MergeSession.Resolution) {
      if (resolution == MergeSession.Resolution.Merged) {
        conflictResolvedForFile(file)
      }
      else {
        val content = getContent(file, resolution == MergeSession.Resolution.AcceptedTheirs)
        if (content == RawText.EMPTY_TEXT) {
          repository.deletePath(file.getPath())
        }
        else {
          addFile(content.getContent(), file)
        }
      }
    }

    private fun getContent(file: VirtualFile, isTheirs: Boolean) = conflicts[file.getPath()]!!.getSequences()[if (isTheirs) 2 else 1] as RawText

    inner class StatusColumn(private val isTheirs: Boolean) : ColumnInfo<VirtualFile, String>(if (isTheirs) "Theirs" else "Yours") {
      override fun valueOf(file: VirtualFile?) = if (getContent(file!!, isTheirs) == RawText.EMPTY_TEXT) "Deleted" else "Modified"

      override fun getMaxStringValue() = "Modified"

      override fun getAdditionalWidth() = 10
    }
  }
}