package com.intellij.markdown.backend.services

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import org.intellij.plugins.markdown.service.VirtualFileAccessor
import java.io.File
import java.net.URL

class VirtualFileAccessorImpl : VirtualFileAccessor {
  private fun getBaseDirectory(projectId: ProjectId, virtualFileId: VirtualFileId) : VirtualFile?{
    val project = projectId.findProject()
    val virtualFile = virtualFileId.virtualFile() ?: return null
    val baseDirectory = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(virtualFile)
    return baseDirectory
  }

  override suspend fun tryToLoadFileContent(resourceName: String, virtualFileId: VirtualFileId, projectId: ProjectId): ByteArray? {
    val projectRoot = getBaseDirectory(projectId, virtualFileId)
    val file = if (resourceName.startsWith("file:/")) {
      VfsUtil.findFileByIoFile(File(URL(resourceName).path), true)
    } else {
      projectRoot?.findFileByRelativePath(resourceName)
    } ?: return null
    if (!file.exists() || file.isDirectory) {
      return null
    }
    val content = file.inputStream.use { it.readBytes() }
    return content
  }

  companion object {
    private val logger: Logger = Logger.getInstance(VirtualFileAccessorImpl::class.java)
  }
}