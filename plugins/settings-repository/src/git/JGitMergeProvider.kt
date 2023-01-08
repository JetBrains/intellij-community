// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ui.ColumnInfo
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.IcsBundle
import org.jetbrains.settingsRepository.RepositoryVirtualFile
import java.nio.CharBuffer

internal fun conflictsToVirtualFiles(map: Map<String, Any>): MutableList<VirtualFile> {
  val result = ArrayList<VirtualFile>(map.size)
  for (path in map.keys) {
    result.add(RepositoryVirtualFile(path))
  }
  return result
}

/**
 * If content null:
 * Ours or Theirs - deleted.
 * Base - missed (no base).
 */
class JGitMergeProvider<T>(private val repository: Repository, private val conflicts: Map<String, T>, private val pathToContent: Map<String, T>.(path: String, index: Int) -> ByteArray?) : MergeProvider2 {
  override fun createMergeSession(files: List<VirtualFile>): MergeSession = JGitMergeSession()

  override fun conflictResolvedForFile(file: VirtualFile) {
    // we can postpone dir cache update (on merge dialog close) to reduce number of flush, but it can leads to data loss (if app crashed during merge - nothing will be saved)
    // update dir cache
    val bytes = (file as RepositoryVirtualFile).byteContent
    // not null if user accepts some revision (virtual file will be directly modified), otherwise document will be modified
    if (bytes == null) {
      val chars = FileDocumentManager.getInstance().getCachedDocument(file)!!.immutableCharSequence
      val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
      addFile(byteBuffer.array(), file, byteBuffer.remaining())
    }
    else {
      addFile(bytes, file)
    }
  }

  private fun addFile(bytes: ByteArray, file: VirtualFile, size: Int = bytes.size) {
    repository.writePath(file.path, bytes)
  }

  override fun isBinary(file: VirtualFile): Boolean = file.fileType.isBinary

  override fun loadRevisions(file: VirtualFile): MergeData {
    val path = file.path
    val mergeData = MergeData()
    mergeData.ORIGINAL = getContentOrEmpty(path, 0)
    mergeData.CURRENT = getContentOrEmpty(path, 1)
    mergeData.LAST = getContentOrEmpty(path, 2)
    return mergeData
  }

  private fun getContentOrEmpty(path: String, index: Int) = conflicts.pathToContent(path, index) ?: ArrayUtilRt.EMPTY_BYTE_ARRAY

  private inner class JGitMergeSession : MergeSession {
    override fun getMergeInfoColumns(): Array<ColumnInfo<out Any?, out Any?>> {
      return arrayOf(StatusColumn(false), StatusColumn(true))
    }

    override fun canMerge(file: VirtualFile) = conflicts.contains(file.path)

    override fun conflictResolvedForFile(file: VirtualFile, resolution: MergeSession.Resolution) {
      if (resolution == MergeSession.Resolution.Merged) {
        conflictResolvedForFile(file)
      }
      else {
        val content = getContent(file, resolution == MergeSession.Resolution.AcceptedTheirs)
        if (content == null) {
          repository.deletePath(file.path)
        }
        else {
          addFile(content, file)
        }
      }
    }

    private fun getContent(file: VirtualFile, isTheirs: Boolean) = conflicts.pathToContent(file.path, if (isTheirs) 2 else 1)

    inner class StatusColumn(private val isTheirs: Boolean) : ColumnInfo<VirtualFile, String>(
      if (isTheirs) IcsBundle.message("merge.settings.column.name.theirs") else IcsBundle.message("merge.settings.column.name.yours"))
    {
      override fun valueOf(file: VirtualFile?) = if (getContent(file!!, isTheirs) == null)
        IcsBundle.message("merge.settings.file.deleted") else IcsBundle.message("merge.settings.file.modified")

      override fun getMaxStringValue() = "Modified"

      override fun getAdditionalWidth() = 10
    }
  }
}