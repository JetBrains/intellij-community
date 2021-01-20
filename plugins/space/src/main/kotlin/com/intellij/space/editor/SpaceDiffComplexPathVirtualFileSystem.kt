// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.space.vcs.review.details.diff.SpaceDiffFileId
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import com.intellij.vcs.editor.GsonComplexPathSerializer

internal class SpaceDiffComplexPathVirtualFileSystem : ComplexPathVirtualFileSystem<SpaceDiffComplexPathVirtualFileSystem.ComplexPath>(
  GsonComplexPathSerializer(ComplexPath::class.java)
) {

  fun getPath(sessionId: String, projectHash: String, fileId: SpaceDiffFileId): String {
    return getPath(ComplexPath(sessionId, projectHash, fileId))
  }

  data class ComplexPath(override val sessionId: String,
                         override val projectHash: String,
                         val fileId: SpaceDiffFileId) : ComplexPathVirtualFileSystem.ComplexPath

  override fun getProtocol(): String = PROTOCOL

  override fun findOrCreateFile(project: Project, path: ComplexPath): VirtualFile? {
    return project.service<SpaceVirtualFilesManager>().findDiffFile(path.fileId)
  }

  companion object {
    private const val PROTOCOL = "space-diff"

    @JvmStatic
    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as SpaceDiffComplexPathVirtualFileSystem
  }
}
