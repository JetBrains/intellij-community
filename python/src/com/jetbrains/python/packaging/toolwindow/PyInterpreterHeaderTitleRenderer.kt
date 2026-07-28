// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ColorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Font

/**
 * Renders the "Python Packages" tool-window title with the active interpreter path appended in
 * a lighter (context-help) foreground. The path start-ellipsizes when the header is too narrow
 * to fit it in full — the tail (`.venv/bin/python`) is what distinguishes environments, so the
 * leading directories are the ones that give way.
 *
 * The renderer piggybacks on the fact that the platform id-label is a plain `JLabel`; wrapping
 * the pieces in HTML with inline styles keeps everything inside the label the platform already
 * paints, so we avoid custom components on the header.
 *
 * The renderer is EDT-affine — call [update] whenever the active SDK changes and [refit] on
 * tool-window resize (or let a caller invoke [refit] directly from a component listener). If
 * the tool window is not resolvable (test environments, disposed project), calls become no-ops.
 */
internal class PyInterpreterHeaderTitleRenderer(
  private val toolWindow: ToolWindow,
  @Nls private val plainTitle: String,
) {
  private var currentPath: String? = null

  @RequiresEdt
  fun update(path: String?) {
    currentPath = path?.takeIf { it.isNotEmpty() }
    refit()
  }

  @RequiresEdt
  fun refit() {
    val path = currentPath
    if (path == null) {
      toolWindow.stripeTitle = plainTitle
      return
    }
    toolWindow.stripeTitle = renderHtml(fitPathToHeader(path))
  }

  private fun fitPathToHeader(path: String): @NlsSafe String {
    val width = toolWindow.component.width.takeIf { it > 0 } ?: return path
    val font = UIUtil.getLabelFont()
    val budget = (width
                  - GraphicsUtil.stringWidth(plainTitle, font.deriveFont(Font.BOLD))
                  - GraphicsUtil.stringWidth("  ", font)
                  - RIGHT_TOOLBAR_RESERVED_PX
                 ).coerceAtLeast(0)
    return shortenTextWithEllipsis(
      text = path,
      minTextPrefixLength = 0,
      minTextSuffixLength = 1,
      maxTextPrefixRatio = 0f,
      maxTextWidth = budget,
      getTextWidth = { GraphicsUtil.stringWidth(it, font) },
      useEllipsisSymbol = true,
    )
  }

  private fun renderHtml(fittedPath: @NlsSafe String): @Nls String {
    val greyHex = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
    val title = HtmlChunk.text(plainTitle).bold()
    val path = HtmlChunk.text(fittedPath).wrapWith(HtmlChunk.span("font-weight:normal;color:$greyHex"))
    return HtmlChunk.html().children(title, HtmlChunk.nbsp(2), path).toString()
  }

  companion object {
    /** Width of the right-side header toolbar (3 action buttons and spaces). */
    private val RIGHT_TOOLBAR_RESERVED_PX: Int get() = 5 * ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width
  }
}
