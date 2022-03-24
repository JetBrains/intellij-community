// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.application.PathManager
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFenceHtmlCache
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.nio.file.Path
import java.nio.file.Paths

abstract class MarkdownCodeFenceCacheableProvider(var collector: MarkdownCodeFencePluginCacheCollector?)
  : CodeFenceGeneratingProvider {


  /**
   * Code fence plugin cache path
   */
  fun getCacheRootPath(): Path {
    return Paths.get(PathManager.getSystemPath(), "markdown", javaClass.simpleName).toAbsolutePath()
  }

  /**
   * Get unique file for this text to cache. md5 will be used to generate file path.
   */
  fun getUniqueFile(language: String, text: String, extension: String): Path {
    val collectorKey = MarkdownUtil.md5(collector?.file?.path, MarkdownCodeFenceHtmlCache.MARKDOWN_FILE_PATH_KEY)
    val fileKey = MarkdownUtil.md5(text, "$language.${extension}")

    return getCacheRootPath().resolve(collectorKey).resolve("$fileKey.$extension")
  }
}
