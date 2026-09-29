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
  private val watcher: MarkdownImageWatcher<*>? = null,
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
    val resolution = awaitWithTimeout(source) { resolve(source, project, document) } ?: return null
    if (resolution is MarkdownPreviewPathResolver.Resolution.Forbidden) {
      thisLogger().warn("The Markdown preview refused $source outside the root of an untrusted project.")
    }
    val file = (resolution as? MarkdownPreviewPathResolver.Resolution.Found)?.file
    watcher?.onImageLoaded(source, file)
    if (file == null) {
      return null
    }
    return runCatching { file.inputStream.use { it.readBytes() } }.getOrNull()
  }

  /** The URL of the image of [source], with the stamp of its file if the file changed. */
  fun imageUrl(source: String): String = imageUrl(this, source, watcher?.versionOf(source))

  /** The file of [source] by the rule of [loadResource]. */
  suspend fun resolveFile(source: String): VirtualFile? {
    val project = project ?: return null
    val document = document ?: return null
    return (resolve(source, project, document) as? MarkdownPreviewPathResolver.Resolution.Found)?.file
  }

  private suspend fun resolve(source: String, project: Project, document: VirtualFile): MarkdownPreviewPathResolver.Resolution {
    return MarkdownPreviewPathResolver.resolve(
      document = document,
      projectRoot = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(document),
      rawSource = source,
      allowOutsideProjectRoot = TrustedProjects.isProjectTrusted(project),
    )
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

    /** The URL of the image of [source]. A new [stamp] makes the browser load the image again. */
    fun imageUrl(provider: ResourceProvider, source: String, stamp: Long?): String {
      val url = PreviewStaticServer.getStaticUrl(provider, resourceName(source))
      if (stamp == null) {
        return url
      }
      val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
      return "${url.substring(0, fragmentStart)}?v=$stamp${url.substring(fragmentStart)}"
    }

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
