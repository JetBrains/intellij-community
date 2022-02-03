// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Enables plugins to provide custom CSS styles for markdown preview. Overrides user settings.
 */
@ApiStatus.Internal
interface MarkdownPreviewStylesProvider {
  fun getStyles(file: VirtualFile): String?

  companion object {
    @get:JvmName("getExtensionPointName")
    internal val EP_NAME: ExtensionPointName<MarkdownPreviewStylesProvider> =
      ExtensionPointName.create("org.intellij.markdown.previewStylesProvider")
  }
}