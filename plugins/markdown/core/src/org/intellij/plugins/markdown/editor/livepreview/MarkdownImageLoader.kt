// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@ApiStatus.Internal
object MarkdownImageLoader {
  @RequiresBackgroundThread
  suspend fun load(project: Project, file: VirtualFile, destination: String): VirtualFile? {
    ThreadingAssertions.assertBackgroundThread()
    return try {
      val baseFile = file.parent ?: return null
      val projectRoot = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file)
                        ?: project.guessProjectDir()
                        ?: return null
      val resolution = MarkdownImagePathResolver.resolve(baseFile, projectRoot, destination)
      if (resolution !is MarkdownImagePathResolver.Resolution.Found) return null
      val imageFile = resolution.file
      if (!imageFile.isValid || imageFile.isDirectory) return null
      if (!isWithinLimits(imageFile)) return null
      imageFile
    }
    catch (_: Exception) {
      null
    }
  }

  private fun isWithinLimits(file: VirtualFile): Boolean {
    val maxBytes = Registry.intValue("markdown.live.preview.image.max.bytes").toLong()
    if (maxBytes < 0 || file.length > maxBytes) return false

    val content = file.contentsToByteArray()
    if (content.size.toLong() > maxBytes) return false

    val maxPixels = Registry.intValue("markdown.live.preview.image.max.pixels").toLong()
    val pixels = if (file.extension.equals("svg", ignoreCase = true)) {
      readSvgPixelCount(content)
    } else {
      readRasterPixelCount(content)
    }
    return pixels != null && pixels <= maxPixels
  }

  private fun readSvgPixelCount(content: ByteArray): Double? {
    return try {
      val size = getSvgDocumentSize(content)
      val width = size.width.toDouble()
      val height = size.height.toDouble()
      if (!width.isFinite() || !height.isFinite()) Double.POSITIVE_INFINITY
      else if (width <= 0 || height <= 0) null
      else width * height
    }
    catch (_: Exception) {
      null
    }
  }

  private fun readRasterPixelCount(content: ByteArray): Double? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(content)) ?: return null
    return input.use { imageInput ->
      val readers = ImageIO.getImageReaders(imageInput)
      if (!readers.hasNext()) return@use null
      val reader = readers.next()
      try {
        reader.setInput(imageInput, true, true)
        val width = reader.getWidth(0)
        val height = reader.getHeight(0)
        if (width <= 0 || height <= 0) return@use null
        width.toDouble() * height.toDouble()
      }
      catch (_: Exception) {
        null
      }
      finally {
        reader.dispose()
      }
    }
  }
}
