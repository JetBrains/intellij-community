// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.jetbrains.annotations.ApiStatus

/**
 * Extension for the JCEF-based browser preview.
 */
@Deprecated("Use MarkdownBrowserPreviewExtension instead.")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
interface MarkdownJCEFPreviewExtension: MarkdownBrowserPreviewExtension {
  /**
   * Map of browser events that should be registered and it's IDE callbacks.
   */
  val events: Map<String, (String) -> Unit>
    get() = emptyMap()
}
