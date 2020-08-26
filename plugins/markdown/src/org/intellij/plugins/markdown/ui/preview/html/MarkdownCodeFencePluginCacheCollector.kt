// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import java.io.File

data class MarkdownCodeFencePluginCacheCollector(val file: VirtualFile) {
  private data class CachedFile(val file: File, val expires: Long)

  private val myAliveCachedFiles: MutableMap<MarkdownCodeFenceCacheableProvider, MutableSet<CachedFile>> = HashMap()

  val aliveCachedFiles: Set<File>
    get() = synchronized(this) {
      val time = System.currentTimeMillis()

      for ((_, files) in myAliveCachedFiles) {
        files.retainAll { it.expires > time }
      }

      return myAliveCachedFiles.values.flatten().map { it.file }.toSet()
    }

  /**
   * Mark file as `alive` meaning it should not be removed during
   * cache cleanup if ttl is not expired
   *
   * @param file that is considered alive
   * @param provider is a provider with which file is associated
   * @param ttlMillis is number of millis file should not be removed
   */
  @Synchronized
  fun addAliveCachedFile(provider: MarkdownCodeFenceCacheableProvider, file: File, ttlMillis: Long = 5 * 60 * 1000) {
    require(ttlMillis > 0) { "TTL of cached file cannot be <=0" }
    val expires = System.currentTimeMillis() + ttlMillis
    myAliveCachedFiles.getOrPut(provider) { HashSet() }.add(CachedFile(file, expires))
  }

  /**
   * Invalidate caches for this provider.
   */
  @Synchronized
  fun invalidate(provider: MarkdownCodeFenceCacheableProvider) {
    myAliveCachedFiles.remove(provider)
  }
}
