// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec.ImageFailure
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec.ImageSource
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewPathResolver.Resolution
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewPathResolver.resolve
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

@ApiStatus.Internal
class LoadedImage(
  val file: VirtualFile?,
  val source: ImageSource? = null,
  val failure: ImageFailure? = null,
)

private const val SCALE_FACTOR = 1024 * 1024

@ApiStatus.Internal
object MarkdownImageLoader {
  private val LOG = Logger.getInstance(MarkdownImageLoader::class.java)

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  suspend fun load(project: Project, file: VirtualFile, destination: String): LoadedImage {
    ThreadingAssertions.assertBackgroundThread()
    return try {
      when (val resolution = resolveImage(project, file, destination)) {
        is Resolution.Found -> read(resolution.file)
        Resolution.Forbidden -> LoadedImage(null, failure = ImageFailure.OUTSIDE_PROJECT)
        Resolution.NotFound -> LoadedImage(null, failure = ImageFailure.NOT_FOUND)
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to resolve Markdown image $destination", e)
      LoadedImage(null, failure = ImageFailure.UNREADABLE)
    }
  }

  suspend fun findImageFile(project: Project, file: VirtualFile, destination: String): VirtualFile? {
    return when (val resolution = resolveImage(project, file, destination)) {
      is Resolution.Found -> resolution.file
      Resolution.Forbidden, Resolution.NotFound -> null
    }
  }

  private suspend fun resolveImage(project: Project, file: VirtualFile, destination: String): Resolution {
    if (!TrustedProjects.isProjectTrusted(project)) return Resolution.NotFound
    val projectRoot = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file)
                      ?: project.guessProjectDir()
                      ?: return Resolution.NotFound
    return resolve(file, projectRoot, destination)
  }

  /** The intrinsic size of [file], or a failure when [file] is not an image inside the registry limits. */
  private fun read(file: VirtualFile): LoadedImage {
    val maxBytes = Registry.intValue("markdown.live.preview.image.max.megabytes") * SCALE_FACTOR
    if (maxBytes < 0 || file.length > maxBytes) return LoadedImage(file, failure = ImageFailure.TOO_LARGE)

    val content = file.contentsToByteArray()
    if (content.size.toLong() > maxBytes) return LoadedImage(file, failure = ImageFailure.TOO_LARGE)

    val isSvg = file.extension.equals("svg", ignoreCase = true)
    val size = if (isSvg) readSvgSize(content) else readRasterSize(content)
    val [width, height] = size ?: return LoadedImage(file, failure = ImageFailure.UNREADABLE)
    val maxPixels = Registry.intValue("markdown.live.preview.image.max.megapixels") * SCALE_FACTOR
    if (width.toDouble() * height > maxPixels) return LoadedImage(file, failure = ImageFailure.TOO_LARGE)
    return LoadedImage(file, ImageSource(file.modificationStamp, width, height))
  }

  private fun readSvgSize(content: ByteArray): Pair<Int, Int>? {
    return try {
      val size = getSvgDocumentSize(content)
      if (!size.width.isFinite() || !size.height.isFinite()) return null
      val width = size.width.roundToInt()
      val height = size.height.roundToInt()
      if (width <= 0 || height <= 0) null else width to height
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to read Markdown SVG image", e)
      null
    }
  }

  private fun readRasterSize(content: ByteArray): Pair<Int, Int>? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(content)) ?: return null
    return input.use { imageInput ->
      val readers = ImageIO.getImageReaders(imageInput)
      if (!readers.hasNext()) return@use null
      val reader = readers.next()
      try {
        reader.setInput(imageInput, true, true)
        val width = reader.getWidth(0)
        val height = reader.getHeight(0)
        if (width <= 0 || height <= 0) null else width to height
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.warn("Failed to read Markdown raster image", e)
        null
      }
      finally {
        reader.dispose()
      }
    }
  }
}
