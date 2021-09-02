// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * Base for any markdown extensions. Implementors of this interface should be
 * registered in extension point section in the plugin.xml.
 */
interface MarkdownExtension {
  companion object {
    internal val EP_NAME: ExtensionPointName<MarkdownExtension> = ExtensionPointName("org.intellij.markdown.markdownExtension")

    val all: Set<MarkdownExtension>
      get() = EP_NAME.extensionList.toSet()

    internal val currentProjectSettings
      get() = MarkdownSettings.getInstance(currentOrDefaultProject(ProjectManager.getInstance().openProjects.firstOrNull()))
  }
}
