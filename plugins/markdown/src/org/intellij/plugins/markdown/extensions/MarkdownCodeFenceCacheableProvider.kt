// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.application.PathManager
import org.intellij.plugins.markdown.ui.preview.MarkdownCodeFencePluginCache
import org.intellij.plugins.markdown.ui.preview.MarkdownCodeFencePluginCacheCollector
import org.intellij.plugins.markdown.ui.preview.MarkdownUtil
import java.nio.file.Path
import java.nio.file.Paths

abstract class MarkdownCodeFenceCacheableProvider(var collector: MarkdownCodeFencePluginCacheCollector?)
  : MarkdownCodeFencePluginGeneratingProvider {


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
    val collectorKey = MarkdownUtil.md5(collector?.file?.path, MarkdownCodeFencePluginCache.MARKDOWN_FILE_PATH_KEY)
    val fileKey = MarkdownUtil.md5(text, "$language.${extension}")

    return getCacheRootPath().resolve(collectorKey).resolve(fileKey)
  }
}