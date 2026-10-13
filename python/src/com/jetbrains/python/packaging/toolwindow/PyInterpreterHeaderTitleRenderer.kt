// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Exposes "Python Packages" (bold) + interpreter path (light "context-help" foreground) as a
 * title action, so the platform renders it inline in the tool window header alongside the other
 * header buttons — no tab-chip background, no stripe-tooltip pollution.
 *
 * `stripeTitle` is unfit to carry the path: it doubles as the stripe-button tooltip source and
 * `HelpTooltip.setPlainTextTitle` XML-escapes it, so HTML markup and the full path would leak
 * into the hover tooltip. Using `setTitle` (content tab title) forces the platform to paint a
 * tab-chip background around the text. A [CustomComponentAction] sidesteps both: the component
 * lives in the header actions row without any tab decoration, and `stripeTitle` stays pinned to
 * the plain "Python Packages" for a clean tooltip.
 *
 * The path start-ellipsizes to the tail (`.venv/bin/python`) — leading directories yield first
 * because environment identity lives in the trailing directory.
 */
internal class PyInterpreterHeaderTitleRenderer(
  private val toolWindow: ToolWindow,
  @Nls private val plainTitle: String,
) {
  init {
    if (toolWindow.stripeTitle != plainTitle) {
      toolWindow.stripeTitle = plainTitle
    }
  }

  private val pathLabel: JBLabel = JBLabel("").apply {
    foreground = UIUtil.getContextHelpForeground()
    isVisible = false
  }

  // Bold "Python Packages" is already drawn by the platform id-label to the left; the header
  // action only contributes the light-gray interpreter path so the two together read as
  // "Python Packages …/path" without repeating the title. The trailing empty inset matches the
  // platform's inter-action horizontal gap so the path does not butt up against the anchor
  // toggle button that follows it in the actions row.
  private val component: JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
    isOpaque = false
    border = JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap())
    add(pathLabel)
  }

  /**
   * The `CustomComponentAction` that hosts the header component. Register via
   * `toolWindow.setTitleActions(listOf(headerAction, …))`; the platform creates the component
   * once through [CustomComponentAction.createCustomComponent] and reuses the same instance for
   * the lifetime of the toolbar.
   */
  val headerAction: AnAction = object : AnAction(), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent = component
    override fun actionPerformed(e: AnActionEvent) {}
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = true
    }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private var currentPath: String? = null
  private var lastRendered: String? = null

  @RequiresEdt
  fun update(path: String?) {
    currentPath = path?.takeIf { it.isNotEmpty() }
    // Always expose the full path via tooltip so the user can inspect the venv name / directory
    // even when the header ellipsizes it or hides it entirely on a narrow tool window (PY-91321).
    // Sat on both the label and its wrapper — an invisible JLabel does not fire tooltip events,
    // so the wrapper carries the tooltip while the header collapses the visible slice.
    pathLabel.toolTipText = currentPath
    (component as JPanel).toolTipText = currentPath
    refit()
  }

  @RequiresEdt
  fun refit() {
    val path = currentPath
    if (path == null) {
      if (pathLabel.isVisible) pathLabel.isVisible = false
      pathLabel.toolTipText = null
      (component as JPanel).toolTipText = null
      lastRendered = null
      return
    }
    val fitted = fitPathToBar(path)
    val text = if (fitted.isEmpty() || fitted.trim().length <= 2) " " else fitted
    if (text == lastRendered && pathLabel.isVisible) return
    lastRendered = text
    pathLabel.text = text
    pathLabel.isVisible = true
    component.revalidate()
    component.repaint()
  }

  private fun fitPathToBar(path: String): @NlsSafe String {
    val width = toolWindow.component.width.takeIf { it > 0 } ?: return path
    val pathFont = pathLabel.font
    val titleFont = pathFont.deriveFont(java.awt.Font.BOLD)
    val budget = (width
                  - GraphicsUtil.stringWidth(plainTitle, titleFont)
                  - HEADER_RIGHT_RESERVED_PX
                 ).coerceAtLeast(0)
    if (budget < MIN_READABLE_WIDTH_PX) return ""
    return shortenTextWithEllipsis(
      text = path,
      minTextPrefixLength = 0,
      minTextSuffixLength = 1,
      maxTextPrefixRatio = 0f,
      maxTextWidth = budget,
      getTextWidth = { GraphicsUtil.stringWidth(it, pathFont) },
      useEllipsisSymbol = true,
    )
  }

  companion object {
    /**
     * Width the platform reserves on the right of the header for the anchor toggle,
     * the gear popup, and the hide button.
     */
    private val HEADER_RIGHT_RESERVED_PX = 5 * ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width +
              2 * JBUI.CurrentTheme.ActionsList.elementIconGap()

    /**
     * Below this budget every rendered slice degenerates to just the ellipsis symbol, so the
     * label is hidden entirely instead.
     */
    private val MIN_READABLE_WIDTH_PX: Int = 2 * ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width
  }
}
