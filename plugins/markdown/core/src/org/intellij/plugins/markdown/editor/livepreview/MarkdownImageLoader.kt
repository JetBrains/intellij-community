// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver.Resolution
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver.resolve
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

@ApiStatus.Internal
class LoadedImage(val file: VirtualFile, val width: Int, val height: Int)

@ApiStatus.Internal
object MarkdownImageLoader {
  private val LOG = Logger.getInstance(MarkdownImageLoader::class.java)

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  suspend fun load(project: Project, file: VirtualFile, destination: String): LoadedImage? {
    ThreadingAssertions.assertBackgroundThread()
    return try {
      val projectRoot = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file)
                        ?: project.guessProjectDir()
                        ?: return null
      val imageFile = (resolve(file, projectRoot, destination) as? Resolution.Found)?.file ?: return null
      if (!imageFile.isValid || imageFile.isDirectory) return null
      val (width, height) = readSize(imageFile) ?: return null
      LoadedImage(imageFile, width, height)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to resolve Markdown image $destination", e)
      null
    }
  }

  /** The intrinsic size of [file], or null when it is not an image inside the registry limits. */
  private fun readSize(file: VirtualFile): Pair<Int, Int>? {
    val maxBytes = Registry.longValue("markdown.live.preview.image.max.bytes")
    if (maxBytes < 0 || file.length > maxBytes) return null

    val content = file.contentsToByteArray()
    if (content.size.toLong() > maxBytes) return null

    val isSvg = file.extension.equals("svg", ignoreCase = true)
    val size = (if (isSvg) readSvgSize(content) else readRasterSize(content)) ?: return null
    val maxPixels = Registry.longValue("markdown.live.preview.image.max.pixels")
    return size.takeIf { (width, height) -> width.toDouble() * height <= maxPixels }
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
