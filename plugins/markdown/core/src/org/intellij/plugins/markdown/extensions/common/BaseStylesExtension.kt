// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.io.isDirectory
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.settings.MarkdownSettingsUtil
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.nio.file.Path
import kotlin.io.path.notExists

internal class BaseStylesExtension(private val project: Project?) : MarkdownBrowserPreviewExtension, ResourceProvider {
  override val priority = MarkdownBrowserPreviewExtension.Priority.BEFORE_ALL

  override val styles: List<String> = listOf("baseStyles/default.css", COLORS_CSS_FILENAME)

  override val resourceProvider: ResourceProvider = this

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    if (resourceName == COLORS_CSS_FILENAME) {
      return ResourceProvider.Resource(PreviewLAFThemeStyles.createStylesheet().toByteArray())
    }
    val customStylesheet = project?.let(this::tryToLoadCustomStylesheet)
    if (customStylesheet != null) {
      return customStylesheet
    }
    return ResourceProvider.loadInternalResource(BaseStylesExtension::class, resourceName)
  }

  private fun tryToLoadCustomStylesheet(project: Project): ResourceProvider.Resource? {
    val settings = MarkdownSettings.getInstance(project)
    if (!settings.useCustomStylesheetPath) {
      return null
    }
    val rawPath = settings.customStylesheetPath ?: return null
    val path = runCatching { Path.of(rawPath) }.getOrNull() ?: return null
    if (path.notExists() || path.isDirectory()) {
      showLoadFailedNotification(project)
      return null
    }
    val belongsToTheProject = runReadAction { MarkdownSettingsUtil.belongsToTheProject(project, path) }
    if (!belongsToTheProject) {
      MarkdownNotifications.showWarning(
        project,
        id = "markdown.custom.stylesheet.unsafe",
        title = MarkdownBundle.message("markdown.notification.unsafe.stylesheet.title"),
        message = MarkdownBundle.message("markdown.notification.unsafe.stylesheet.outside.project.text")
      )
      return null
    }
    val resource = ResourceProvider.loadExternalResource(path)
    if (resource == null) {
      showLoadFailedNotification(project)
    }
    return resource
  }

  private fun showLoadFailedNotification(project: Project) {
    MarkdownNotifications.showWarning(
      project,
      id = "markdown.custom.stylesheet.load.failed",
      title = MarkdownBundle.message("markdown.notification.unsafe.stylesheet.title"),
      message = MarkdownBundle.message("markdown.notification.unsafe.stylesheet.load.error.text")
    )
  }

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return BaseStylesExtension(panel.project)
    }
  }

  companion object {
    private const val COLORS_CSS_FILENAME = "baseStyles/colors.css"
  }
}
