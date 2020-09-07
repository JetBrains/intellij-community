// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Base for any markdown extensions. Implementors of this interface should be
 * registered in extension point section in the plugin.xml.
 */
interface MarkdownExtension {
  companion object {
    val EP_NAME: ExtensionPointName<MarkdownExtension> = ExtensionPointName.create(
      "org.intellij.markdown.markdownExtension"
    )

    @JvmStatic
    val all: Set<MarkdownExtension>
      get() = EP_NAME.extensions.toSet()
  }
}
