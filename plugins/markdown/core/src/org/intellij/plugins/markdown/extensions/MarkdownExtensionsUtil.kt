// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.Icon

@ApiStatus.Internal
object MarkdownExtensionsUtil {
  fun collectConfigurableExtensions(enabledOnly: Boolean = false): Sequence<MarkdownConfigurableExtension> {
    val generatingProviders = CodeFenceGeneratingProvider.all.asSequence().filterIsInstance<MarkdownConfigurableExtension>()
    val previewExtensions = MarkdownBrowserPreviewExtension.Provider.all.asSequence().filterIsInstance<MarkdownConfigurableExtension>()
    val all = generatingProviders + previewExtensions
    return when {
      enabledOnly -> all.filter { it.isEnabled }
      else -> all
    }
  }

  fun collectExtensionsWithExternalFiles(): Sequence<MarkdownExtensionWithExternalFiles> {
    return collectConfigurableExtensions().filterIsInstance<MarkdownExtensionWithExternalFiles>()
  }

  inline fun <reified T: MarkdownBrowserPreviewExtension.Provider> findBrowserExtensionProvider(): T? {
    return MarkdownBrowserPreviewExtension.Provider.EP.findExtension(T::class.java)
  }

  inline fun <reified T: CodeFenceGeneratingProvider> findCodeFenceGeneratingProvider(): T? {
    return CodeFenceGeneratingProvider.EP_NAME.findExtension(T::class.java)
  }

  fun loadIcon(icon: Icon, format: String): ByteArray {
    val output = ByteArrayOutputStream()
    val fontSize = JBCefApp.normalizeScaledSize(EditorUtil.getEditorFont().size + 1).toFloat()
    val scaledIcon = IconUtil.scaleByFont(icon, null, fontSize)
    val image = ImageUtil.createImage(
      ScaleContext.create(),
      scaledIcon.iconWidth.toDouble(),
      scaledIcon.iconHeight.toDouble(),
      BufferedImage.TYPE_INT_ARGB,
      PaintUtil.RoundingMode.FLOOR
    )
    scaledIcon.paintIcon(null, image.graphics, 0, 0)
    ImageIO.write(image, format, output)
    return output.toByteArray()
  }
}
