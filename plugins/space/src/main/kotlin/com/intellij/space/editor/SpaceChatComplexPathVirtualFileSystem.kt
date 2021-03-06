// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import com.intellij.vcs.editor.GsonComplexPathSerializer

internal class SpaceChatComplexPathVirtualFileSystem : ComplexPathVirtualFileSystem<SpaceChatComplexPathVirtualFileSystem.ComplexPath>(
  GsonComplexPathSerializer(ComplexPath::class.java)
) {

  fun getPath(sessionId: String, projectHash: String, id: String): String {
    return getPath(ComplexPath(sessionId, projectHash, id))
  }

  data class ComplexPath(override val sessionId: String,
                         override val projectHash: String,
                         val id: String) : ComplexPathVirtualFileSystem.ComplexPath

  override fun getProtocol(): String = PROTOCOL

  override fun findOrCreateFile(project: Project, path: ComplexPath): VirtualFile? {
    return project.service<SpaceVirtualFilesManager>().findChatFile(path.id)
  }

  companion object {
    private const val PROTOCOL = "space-chat"

    @JvmStatic
    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as SpaceChatComplexPathVirtualFileSystem
  }
}
