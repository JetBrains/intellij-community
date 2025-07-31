// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.application.PathManager
import com.intellij.ide.caches.CachesInvalidator
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class HighlighterCache() : CachesInvalidator() {
  private val cacheDir: Path = PathManager.getSystemPath().let { systemPath ->
    val dir = File(systemPath, CACHE_NAME).toPath()

    if (!dir.exists()) {
      Files.createDirectories(dir)
    }
    dir
  }

  @Throws(IOException::class)
  fun writeCache(data: String) {
    val cacheFile = cacheDir.resolve("$CACHE_NAME.cache")

    if (!cacheFile.exists()) {
      cacheFile.createFile()
    }
    cacheFile.writeText(data)
  }

  @Throws(IOException::class)
  fun readCache(): String? {
    val cacheFile = cacheDir.resolve("$CACHE_NAME.cache")

    return if (cacheFile.exists()) {
      cacheFile.readText()
    } else {
      null
    }
  }

  override fun invalidateCaches() {
    Files.newDirectoryStream(cacheDir).use { stream ->
      stream.forEach { file ->
        file.toFile().delete()
      }
    }
  }

  companion object {
    private const val CACHE_NAME = "highlight-js"
  }
}
