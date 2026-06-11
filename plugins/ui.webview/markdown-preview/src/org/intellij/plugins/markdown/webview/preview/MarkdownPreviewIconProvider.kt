// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.icons.AllIcons
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetProvider
import com.intellij.ui.webview.api.WebViewAssetProviderResult
import com.intellij.util.ui.ImageUtil
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.Icon

internal class MarkdownPreviewIconProvider : WebViewAssetProvider {
  override fun resolve(path: WebViewAssetPath): WebViewAssetProviderResult {
    val icon = when (path.path) {
      RUN_LINE_ICON -> AllIcons.RunConfigurations.TestState.Run
      RUN_BLOCK_ICON -> AllIcons.RunConfigurations.TestState.Run_run
      else -> return WebViewAssetProviderResult.NotFound("Markdown preview icon not found: $path")
    }
    return WebViewAssetProviderResult.Content.of(
      contentType = "image/png",
      bytes = icon.toPngBytes(),
      headers = mapOf("Cache-Control" to "no-cache"),
    )
  }

  private fun Icon.toPngBytes(): ByteArray {
    val image = ImageUtil.createImage(iconWidth.coerceAtLeast(1), iconHeight.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      paintIcon(null, graphics, 0, 0)
    }
    finally {
      graphics.dispose()
    }
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "png", output)
    return output.toByteArray()
  }

  companion object {
    const val RUN_LINE_ICON: String = "run.png"
    const val RUN_BLOCK_ICON: String = "runBlock.png"
  }
}

internal const val MARKDOWN_ICON_PREFIX: String = "__markdown-preview-icon"
