/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.ColumnInfo
import org.eclipse.jgit.lib.Repository
import org.jetbrains.jgit.dirCache.deletePath
import org.jetbrains.jgit.dirCache.writePath
import org.jetbrains.settingsRepository.RepositoryVirtualFile
import java.nio.CharBuffer
import java.util.*

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
    val bytes = (file as RepositoryVirtualFile).content
    // not null if user accepts some revision (virtual file will be directly modified), otherwise document will be modified
    if (bytes == null) {
      val chars = FileDocumentManager.getInstance().getCachedDocument(file)!!.immutableCharSequence
      val byteBuffer = CharsetToolkit.UTF8_CHARSET.encode(CharBuffer.wrap(chars))
      addFile(byteBuffer.array(), file, byteBuffer.remaining())
    }
    else {
      addFile(bytes, file)
    }
  }

  private fun addFile(bytes: ByteArray, file: VirtualFile, size: Int = bytes.size) {
    repository.writePath(file.path, bytes, size)
  }

  override fun isBinary(file: VirtualFile) = file.fileType.isBinary

  override fun loadRevisions(file: VirtualFile): MergeData {
    val path = file.path
    val mergeData = MergeData()
    mergeData.ORIGINAL = getContentOrEmpty(path, 0)
    mergeData.CURRENT = getContentOrEmpty(path, 1)
    mergeData.LAST = getContentOrEmpty(path, 2)
    return mergeData
  }

  private fun getContentOrEmpty(path: String, index: Int) = conflicts.pathToContent(path, index) ?: ArrayUtil.EMPTY_BYTE_ARRAY

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

    inner class StatusColumn(private val isTheirs: Boolean) : ColumnInfo<VirtualFile, String>(if (isTheirs) "Theirs" else "Yours") {
      override fun valueOf(file: VirtualFile?) = if (getContent(file!!, isTheirs) == null) "Deleted" else "Modified"

      override fun getMaxStringValue() = "Modified"

      override fun getAdditionalWidth() = 10
    }
  }
}