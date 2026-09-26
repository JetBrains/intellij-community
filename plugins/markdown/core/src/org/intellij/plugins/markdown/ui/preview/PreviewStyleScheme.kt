// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle.AlertType
import org.intellij.plugins.markdown.lang.psi.util.alertTitleColorKey
import java.awt.Color

class PreviewStyleScheme(
  val fontSize: Int,
  val scale: Float,
  val backgroundColor: Color,
  val foregroundColor: Color,
  val linkActiveForegroundColor: Color,
  val infoForegroundColor: Color,
  val separatorColor: Color,
  val fenceBackgroundColor: Color,
  val alertColors: Map<String, Color>
) {

  companion object {
    fun fromCurrentTheme(): PreviewStyleScheme {
      val scheme = obtainColorScheme()

      val contrastedForeground = scheme.defaultForeground.contrast(0.1)

      val panelBackground = UIUtil.getPanelBackground()
      val backgroundColor = scheme.defaultBackground

      val linkActiveForeground = JBUI.CurrentTheme.Link.Foreground.ENABLED
      val separatorColor = ensureContrast(
        background = backgroundColor,
        color = JBColor.namedColor("Group.separatorColor", panelBackground),
        requiredContrast = MIN_BORDER_CONTRAST,
      )
      val infoForeground = JBColor.namedColor("Component.infoForeground", contrastedForeground)

      val markdownFenceBackground = JBColor(Color(212, 222, 231, 255 / 4), Color(212, 222, 231, 25))
      val fontSize = PreviewLAFThemeStyles.defaultFontSize
      val scale = service<UISettings>().currentIdeScale
      return PreviewStyleScheme(
        fontSize = fontSize,
        scale = scale,
        backgroundColor = backgroundColor,
        foregroundColor = scheme.defaultForeground,
        linkActiveForegroundColor = linkActiveForeground,
        infoForegroundColor = infoForeground,
        separatorColor = separatorColor,
        fenceBackgroundColor = markdownFenceBackground,
        alertColors = AlertType.entries.associate { type ->
          type.name.lowercase() to scheme.alertColor(type)
        },
      )
    }

    private fun EditorColorsScheme.alertColor(type: AlertType): Color {
      return getAttributes(alertTitleColorKey(type))?.foregroundColor ?: defaultForeground
    }

    private fun obtainColorScheme(): EditorColorsScheme {
      val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
      return manager.schemeManager.activeScheme ?: DefaultColorSchemesManager.getInstance().firstScheme
    }

    /**
     * Simple linear contrast function.
     *
     * 0 < coefficient < 1 results in reduced contrast.
     * coefficient > 1 results in increased contrast.
     */
    private fun Color.contrast(coefficient: Double): Color {
      @Suppress("UseJBColor")
      return Color(
        (coefficient * (red - 128) + 128).toInt(),
        (coefficient * (green - 128) + 128).toInt(),
        (coefficient * (blue - 128) + 128).toInt(),
        alpha
      )
    }

    private const val MIN_BORDER_CONTRAST = 3f

    private fun ensureContrast(background: Color, color: Color, requiredContrast: Float): Color {
      val shift = if (ColorUtil.getLuminance(color) >= ColorUtil.getLuminance(background)) ColorUtil::brighter else ColorUtil::darker
      var tones = 0
      var shifted = color
      while (ColorUtil.getContrast(shifted, background) < requiredContrast) {
        val next = shift(color, ++tones)
        if (next == shifted) break
        shifted = next
      }
      return shifted
    }
  }
}
