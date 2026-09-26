// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.jetbrains.annotations.ApiStatus

/**
 * Extensions that implement this interface will be added to
 * the extensions table on the markdown settings page.
 */
@ApiStatus.Obsolete
@ApiStatus.Internal
interface MarkdownConfigurableExtension {
  /**
   * Name that will be displayed in the extensions table.
   */
  val displayName: String

  /**
   * Extension description that will be shown in the tooltip on the settings page.
   */
  val description: String

  val isEnabled: Boolean
    get() = MarkdownExtensionsSettings.getInstance().isExtensionEnabled(id)

  /**
   * Unique id for the extension.
   */
  val id: String
}
