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
  fun collectConfigurableExtensions(enabledOnly: Boolean = false): Set<MarkdownConfigurableExtension> {
    val extensions = CodeFenceGeneratingProvider.all.filterIsInstance<MarkdownConfigurableExtension>().toMutableSet()
    extensions.addAll(MarkdownBrowserPreviewExtension.Provider.all.filterIsInstance<MarkdownConfigurableExtension>())
    return when {
      enabledOnly -> extensions.filter { it.isEnabled }.toSet()
      else -> extensions
    }
  }

  inline fun <reified T> findBrowserExtensionProvider(): T? {
    return MarkdownBrowserPreviewExtension.Provider.all.find { it is T } as? T
  }

  fun loadIcon(icon: Icon, format: String): ByteArray {
    val output = ByteArrayOutputStream()
    val fontSize = JBCefApp.normalizeScaledSize(EditorUtil.getEditorFont().size + 1).toFloat()
    //MarkdownExtension.currentProjectSettings.fontSize.toFloat()
    val scaledIcon = IconUtil.scaleByFont(icon, null, fontSize)
    val image = ImageUtil.createImage(ScaleContext.create(), scaledIcon.iconWidth.toDouble(), scaledIcon.iconHeight.toDouble(),
                                      BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.FLOOR)
    scaledIcon.paintIcon(null, image.graphics, 0, 0)

    //val image = IconUtil.toBufferedImage(scaledIcon, true)
    ImageIO.write(image, format, output)
    return output.toByteArray()
  }
}
