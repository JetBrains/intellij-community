// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File

/**
 * The exported HTML document and the images that the document refers to by a relative path.
 *
 * The images are empty when the document embeds them.
 */
@ApiStatus.Internal
class MarkdownHtmlExportResult(val html: String, val images: List<Image>) {
  class Image(val name: String, val content: ByteArray)

  /**
   * Writes the document in UTF-8 to [targetFile] and each image to [resourceDir].
   */
  fun writeTo(targetFile: File, resourceDir: String) {
    for (image in images) {
      val imageFile = File(resourceDir, image.name)
      FileUtil.createIfDoesntExist(imageFile)
      imageFile.writeBytes(image.content)
    }
    targetFile.writeText(html, Charsets.UTF_8)
  }

  companion object {
    /**
     * Returns the directory that an exported `img` element names in its `src` to find an image in [resourceDir].
     */
    fun getImagesSrcDir(targetFile: File, resourceDir: String): String {
      val relativePath = FileUtil.getRelativePath(targetFile.parentFile, File(resourceDir)) ?: resourceDir
      return FileUtil.toSystemIndependentName(relativePath)
    }
  }
}
