package com.intellij.markdown.backend.services

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import org.intellij.plugins.markdown.service.VirtualFileAccessor
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver

/**
 * Reads a Markdown preview resource for a Remote Development frontend.
 *
 * The frontend has no directory tree, so it sends the source and this side resolves it with the
 * same resolver that the monolith uses. See IJPL-254292.
 */
class VirtualFileAccessorImpl : VirtualFileAccessor {
  override suspend fun tryToLoadFileContent(resourceName: String, virtualFileId: VirtualFileId, projectId: ProjectId): ByteArray? {
    val project = projectId.findProjectOrNull() ?: return null
    val document = virtualFileId.virtualFile() ?: return null
    val projectRoot = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(document)
    val resolution = MarkdownImagePathResolver.resolve(
      document = document,
      projectRoot = projectRoot,
      rawSource = resourceName,
      allowOutsideProjectRoot = TrustedProjects.isProjectTrusted(project),
    )
    if (resolution is MarkdownImagePathResolver.Resolution.Forbidden) {
      logger.warn("The Markdown preview refused $resourceName outside the root of an untrusted project.")
      return null
    }
    val file = (resolution as? MarkdownImagePathResolver.Resolution.Found)?.file ?: return null
    return runCatching { file.inputStream.use { it.readBytes() } }.getOrNull()
  }

  override suspend fun tryToFindFileByUrl(url: String): VirtualFileId? =
    VirtualFileManager.getInstance().findFileByUrl(url)?.rpcId()

  companion object {
    private val logger: Logger = Logger.getInstance(VirtualFileAccessorImpl::class.java)
  }
}
