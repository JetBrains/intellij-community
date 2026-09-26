// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.jcef

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.util.Base64

private const val THEME_DEFINITION_FILENAME = "mermaid/themeDefinition.js"

internal const val ZOOM_ICONS_STYLESHEET: String = "mermaid-zoom-icons.css"

private val ZOOM_ICONS = mapOf(
  "mermaid-zoom-out" to "/expui/image/zoomOut.svg",
  "mermaid-zoom-in" to "/expui/image/zoomIn.svg",
  "mermaid-zoom-reset" to "/expui/image/fitContent.svg",
)

internal fun buildZoomIconStylesheet(): String {
  return ZOOM_ICONS.entries.joinToString(separator = "\n\n") { (styleClass, iconPath) ->
    val icon = AllIcons::class.java.getResourceAsStream(iconPath)?.use { it.readBytes() }
    if (icon == null) {
      logger<MermaidBrowserExtension>().warn("Mermaid zoom icon for '$styleClass' is missing at '$iconPath'")
      return@joinToString ""
    }
    val encoded = Base64.getEncoder().encodeToString(icon)
    // language=CSS
    """
    .$styleClass::before {
        mask-image: url("data:image/svg+xml;base64,$encoded");
        -webkit-mask-image: url("data:image/svg+xml;base64,$encoded");
    }
    """.trimIndent()
  }
}

internal class MermaidBrowserExtension : MarkdownBrowserPreviewExtension, ResourceProvider {

  override val scripts: List<String> = listOf(
    THEME_DEFINITION_FILENAME,
    "mermaid.js",
  )

  override val styles: List<String> = listOf("mermaid.css", ZOOM_ICONS_STYLESHEET)

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts || resourceName in styles || resourceName == "mermaid.js.map"
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return when (resourceName) {
      THEME_DEFINITION_FILENAME -> ResourceProvider.Resource(
        "window.mermaidTheme = '${determineMermaidTheme()}';".toByteArray()
      )

      ZOOM_ICONS_STYLESHEET -> ResourceProvider.Resource(buildZoomIconStylesheet().toByteArray(), "text/css")

      else -> ResourceProvider.loadInternalResource(this::class.java, resourceName)
    }
  }

  override val resourceProvider: ResourceProvider = this

  override fun dispose() {}

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return MermaidBrowserExtension()
    }
  }
}
