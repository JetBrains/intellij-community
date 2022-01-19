// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.util.io.delete
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*

@ApiStatus.Experimental
abstract class ExtensionsExternalFilesPathManager {
  abstract val baseDirectory: Path

  /**
   * Obtains path to the dedicated external files directory for the [extension].
   *
   * **You are not supposed to cache this path!**
   *
   * **Returned path might not exist yet!**
   */
  fun obtainExternalFilesDirectoryPath(extension: MarkdownExtensionWithExternalFiles): Path {
    val directoryName = extension.id.lowercase(Locale.getDefault())
    return baseDirectory.resolve(directoryName)
  }

  /**
   * Cleans up all external files for the [extension].
   * The dedicated directory for specified extensions will be also removed.
   * Will call [MarkdownExtensionWithExternalFiles.beforeCleanup] before actually deleting any files.
   */
  fun cleanupExternalFiles(extension: MarkdownExtensionWithExternalFiles) {
    extension.beforeCleanup()
    val path = obtainExternalFilesDirectoryPath(extension)
    path.delete(recursively = true)
  }

  @ApiStatus.Internal
  internal class Impl: ExtensionsExternalFilesPathManager() {
    override val baseDirectory: Path
      get() = Path.of(PathManager.getSystemPath(), "markdown", "download")
  }

  companion object {
    @JvmStatic
    fun getInstance(): ExtensionsExternalFilesPathManager {
      return service()
    }

    fun MarkdownExtensionWithExternalFiles.obtainExternalFilesDirectoryPath(): Path {
      return getInstance().obtainExternalFilesDirectoryPath(this)
    }
  }
}
