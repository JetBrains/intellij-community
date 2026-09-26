// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.plugins.markdown.service.VirtualFileAccessor
import org.intellij.plugins.markdown.ui.preview.html.PreviewEncodingUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class MarkdownImageResourceProvider(
  private val project: Project?,
  private val document: VirtualFile?,
) : ResourceProvider {
  override fun canProvide(resourceName: String): Boolean = resourceName.startsWith(PREFIX)

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    val source = decodeSource(resourceName) ?: return null
    val content = loadContent(source) ?: return null
    return ResourceProvider.Resource(content)
  }

  private fun loadContent(source: String): ByteArray? {
    val project = project ?: return null
    val document = document ?: return null
    // No parent means a Remote Development frontend. Only the backend can resolve the source.
    if (document.parent == null) {
      return loadFromBackend(source, document, project)
    }
    val projectRoot = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(document)
    val resolution = awaitWithTimeout(source) {
      MarkdownImagePathResolver.resolve(
        document = document,
        projectRoot = projectRoot,
        rawSource = source,
        allowOutsideProjectRoot = TrustedProjects.isProjectTrusted(project),
      )
    } ?: return null
    if (resolution is MarkdownImagePathResolver.Resolution.Forbidden) {
      thisLogger().warn("The Markdown preview refused $source outside the root of an untrusted project.")
      return null
    }
    val file = (resolution as? MarkdownImagePathResolver.Resolution.Found)?.file ?: return null
    return runCatching { file.inputStream.use { it.readBytes() } }.getOrNull()
  }

  private fun loadFromBackend(source: String, document: VirtualFile, project: Project): ByteArray? {
    val accessor = VirtualFileAccessor.tryGetInstance() ?: return null
    val documentId = document.rpcId()
    val projectId = project.projectId()
    val outcome = awaitWithTimeout(source) {
      runCatching { accessor.tryToLoadFileContent(source, documentId, projectId) }
    } ?: return null
    return outcome.getOrNull()
  }

  private fun <T> awaitWithTimeout(source: String, action: suspend () -> T): T? {
    val result = runBlockingMaybeCancellable { withTimeoutOrNull(LOAD_TIMEOUT) { action() } }
    if (result == null) {
      thisLogger().warn("The Markdown preview gave up on $source after $LOAD_TIMEOUT.")
    }
    return result
  }

  companion object {
    private const val PREFIX = "image/"
    private val LOAD_TIMEOUT = 10.seconds

    fun resourceName(source: String): String {
      val extension = source.substringAfterLast('/').substringAfterLast('.', "")
      val encoded = PreviewEncodingUtil.encodeUrlSafe(source)
      return if (extension.isEmpty()) PREFIX + encoded else "$PREFIX$encoded.$extension"
    }

    @VisibleForTesting
    fun decodeSource(resourceName: String): String? {
      if (!resourceName.startsWith(PREFIX)) {
        return null
      }
      val body = resourceName.substring(PREFIX.length)
      // Base64 for a URL holds no dot, so a dot can only start the extension.
      val encoded = body.substringBeforeLast('.')
      return PreviewEncodingUtil.decodeUrlSafe(encoded)
    }
  }
}
