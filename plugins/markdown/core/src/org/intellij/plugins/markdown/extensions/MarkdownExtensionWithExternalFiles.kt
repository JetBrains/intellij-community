// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.util.io.exists
import org.intellij.plugins.markdown.extensions.ExtensionsExternalFilesPathManager.Companion.obtainExternalFilesDirectoryPath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface MarkdownExtensionWithExternalFiles: MarkdownConfigurableExtension {
  val externalFiles: Iterable<String>

  val isAvailable: Boolean get() {
    val externalFilesDirectory = obtainExternalFilesDirectoryPath()
    return externalFiles.all { externalFilesDirectory.resolve(it).exists() }
  }

  override val isEnabled: Boolean
    get() = super.isEnabled && isAvailable

  /**
   * This method will be called before cleaning up any files by [ExtensionsExternalFilesPathManager.cleanupExternalFiles].
   */
  fun beforeCleanup() = Unit
}
