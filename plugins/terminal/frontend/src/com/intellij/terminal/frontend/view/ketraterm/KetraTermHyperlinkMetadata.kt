/*
 * Copyright 2026 Gagik Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.terminal.frontend.view.ketraterm

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.filters.HyperlinkWithHoverInfo
import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import io.github.ketraterm.render.api.TerminalRenderUnderline
import io.github.ketraterm.ui.swing.api.SwingHyperlinkAction
import io.github.ketraterm.ui.swing.api.SwingHyperlinkPresentation
import io.github.ketraterm.ui.swing.api.SwingHyperlinkStyle
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent

/** Public console styles are resolved during discovery, outside Swing painting. */
internal fun intellijHyperlinkPresentation(item: Filter.ResultItem): SwingHyperlinkPresentation {
  if (item.isInvisibleLink) return intellijImplicitHyperlinkPresentation()
  val scheme = EditorColorsManager.getInstance().globalScheme
  val linkAttributes = scheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)
  val normal = item.highlightAttributes ?: linkAttributes
  return SwingHyperlinkPresentation(
    normal = normal.toSwingStyle(),
    // The console's global followed style is a navigation marker, not a visited-URL style.
    followed = item.followedHyperlinkAttributes?.toSwingStyle(),
    isVisible = true,
  )
}

/** Terminal-authored links keep terminal colors until activation. */
internal fun intellijOsc8HyperlinkPresentation(
  scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme,
): SwingHyperlinkPresentation = SwingHyperlinkPresentation(
  normal = SwingHyperlinkStyle(underlineStyle = TerminalRenderUnderline.DOTTED),
  hovered = SwingHyperlinkStyle(underlineStyle = TerminalRenderUnderline.SINGLE, underlineThickness = 2),
  active = scheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).toSwingStyle(),
  isVisible = true,
)

/** Implicit discovered paths use the IDE's theme-resolved hover/modifier policy. */
internal fun intellijImplicitHyperlinkPresentation(
  scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme,
): SwingHyperlinkPresentation {
  val resting = SwingHyperlinkStyle(underlineStyle = TerminalRenderUnderline.NONE)
  return SwingHyperlinkPresentation(
    normal = resting,
    hovered =
      SwingHyperlinkStyle(
        underlineArgb = ColorUtil.withAlpha(scheme.defaultForeground, if (JBColor.isBright()) 0.4 else 0.5).rgb,
        underlineStyle = TerminalRenderUnderline.SINGLE,
      ),
    active = scheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).toSwingStyle(),
    followed = resting,
    isVisible = false,
  )
}

private fun TextAttributes?.toSwingStyle(): SwingHyperlinkStyle =
  if (this == null) {
    SwingHyperlinkStyle()
  }
  else {
    SwingHyperlinkStyle(
      foregroundArgb = foregroundColor?.rgb,
      backgroundArgb = backgroundColor?.rgb,
      underlineArgb = effectColor?.rgb,
      underlineThickness = if (effectType == EffectType.BOLD_LINE_UNDERSCORE || effectType == EffectType.BOLD_DOTTED_LINE) 2 else 1,
      underlineStyle =
        when (effectType) {
          EffectType.LINE_UNDERSCORE, EffectType.BOLD_LINE_UNDERSCORE -> TerminalRenderUnderline.SINGLE
          EffectType.WAVE_UNDERSCORE -> TerminalRenderUnderline.CURLY
          EffectType.BOLD_DOTTED_LINE -> TerminalRenderUnderline.DOTTED
          else -> TerminalRenderUnderline.NONE
        },
    )
  }

/** Adapts navigation, hover events, and popup actions to the native renderer. */
internal class KetraTermHyperlinkAction(
  private val project: Project,
  private val hyperlink: HyperlinkInfo,
) : SwingHyperlinkAction {
  private val hoverBounds = if (hyperlink is HyperlinkWithHoverInfo) Rectangle() else null

  override fun open(): Boolean {
    if (project.isDisposed) return false
    hyperlink.navigate(project)
    return true
  }

  override fun open(event: MouseEvent): Boolean {
    if (project.isDisposed) return false
    if (hyperlink is HyperlinkInfoBase) hyperlink.navigate(project, RelativePoint(event)) else hyperlink.navigate(project)
    return true
  }

  override fun mouseEntered(
    component: JComponent,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    if (project.isDisposed) return
    val bounds = hoverBounds ?: return
    bounds.setBounds(x, y, width, height)
    (hyperlink as HyperlinkWithHoverInfo).onMouseEntered(component, bounds)
  }

  override fun mouseExited() {
    (hyperlink as? HyperlinkWithHoverInfo)?.onMouseExited()
  }

  fun popupGroup(event: MouseEvent): ActionGroup? =
    if (project.isDisposed) null else (hyperlink as? HyperlinkWithPopupMenuInfo)?.getPopupMenuGroup(event)
}
