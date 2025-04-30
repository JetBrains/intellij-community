// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.VisualLinesIterator
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.ui.*
import kotlin.math.max

/**
 * Manages the vertical scroll offset of the terminal output:
 * 1. Adjusts it to follow the cursor offset or last non-blank line (depending on what is located lower)
 * if the user does not modify scrolling position manually.
 * 2. Provides an ability to scroll to cursor: [scrollToCursor]
 *
 * Lifecycle is bound to the provided Coroutine Scope.
 */
internal class TerminalOutputScrollingModelImpl(
  private val editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val sessionModel: TerminalSessionModel,
  coroutineScope: CoroutineScope,
) : TerminalOutputScrollingModel {
  private var shouldScrollToCursor: Boolean = true

  init {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      outputModel.cursorOffsetState.collect { offset ->
        if (shouldScrollToCursor) {
          scrollToCursor(offset)
        }
      }
    }

    outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int) {
        if (shouldScrollToCursor) {
          // We already called in an EDT, but let's update the scroll later to not block output model updates.
          coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            scrollToCursor(outputModel.cursorOffsetState.value)
          }
        }
      }
    })

    editor.scrollingModel.addVisibleAreaListener(object : VisibleAreaListener {
      override fun visibleAreaChanged(e: VisibleAreaEvent) {
        // Filter programmatic changes of the scroll offset to handle only changes happened
        // in a result of user actions (mouse wheel scroll, scrollbar thumb dragging).
        if (editor.isTerminalOutputScrollChangingActionInProgress) {
          return
        }

        val endY = e.newRectangle.y + e.newRectangle.height
        if (endY == editor.contentSize.height) {
          // The user has returned the scrollbar to the bottom, so we should stick to the bottom again.
          shouldScrollToCursor = true
        }
        else if (e.oldRectangle != null && e.oldRectangle.y != e.newRectangle.y) {
          // The user has changed the scroll offset, so we should stop following the cursor.
          shouldScrollToCursor = false
        }
      }
    }, coroutineScope.asDisposable())
  }

  @RequiresEdt
  override fun scrollToCursor(force: Boolean) {
    if (force) {
      shouldScrollToCursor = true
    }
    if (shouldScrollToCursor) {
      scrollToCursor(outputModel.cursorOffsetState.value)
    }
  }

  /**
   * The visible area of the terminal output is bound to the screen area - the last output lines that fit into the screen height.
   * But given that we have blocks with additional vertical insets, the same number of lines may require more height than we have.
   * The terminal should show the first screen line at the top of the viewport.
   * But if we follow this rule, the line with cursor or just the last non-blank line can occur below the viewport bounds
   * because actual height is increased by the block insets.
   *
   * So, this method is trying to adjust the scroll offset to put the first line of the screen to the top of the viewport.
   * But if the cursor or last non-blank line becomes out of viewport, we increase the offset to make them visible.
   */
  private fun scrollToCursor(offset: Int) {
    val screenRows = editor.calculateTerminalSize()?.rows ?: return

    val screenBottomVisualLine = editor.offsetToVisualLine(editor.document.textLength, true)
    val screenTopVisualLine = max(0, screenBottomVisualLine - screenRows + 1)

    val topInset = getTopInset()
    val bottomInset = JBUI.scale(TerminalUi.blockBottomInset)

    val lastNotBlankVisualLine = findLastNotBlankVisualLine(screenTopVisualLine)
    val lastNotBlankLineBottomY = editor.visualLineToY(lastNotBlankVisualLine) + editor.lineHeight + bottomInset
    val cursorBottomY = editor.offsetToXY(offset).y + editor.lineHeight + bottomInset
    val screenBottomY = max(lastNotBlankLineBottomY, cursorBottomY)

    val screenTopY = editor.visualLineToY(screenTopVisualLine) - topInset
    val screenHeight = editor.scrollingModel.visibleArea.height

    val scrollY = max(screenBottomY - screenHeight, screenTopY)

    editor.doTerminalOutputScrollChangingAction {
      editor.doWithoutScrollingAnimation {
        editor.scrollingModel.scrollVertically(scrollY)
      }
    }
  }

  private fun findLastNotBlankVisualLine(startVisualLine: Int): Int {
    var lastNotBlankVisualLine = startVisualLine

    // We need to find the last non-blank line, so it would be better to search from the end.
    // But I didn't find such an option, so let's search from the start.
    // It should not be a big deal since screen height is usually small.
    val iterator = VisualLinesIterator(editor as EditorImpl, startVisualLine)
    while (!iterator.atEnd()) {
      val startOffset = iterator.visualLineStartOffset
      val endOffset = iterator.visualLineEndOffset
      if (editor.document.charsSequence.subSequence(startOffset, endOffset).isNotBlank()) {
        lastNotBlankVisualLine = iterator.getVisualLine()
      }
      iterator.advance()
    }

    return lastNotBlankVisualLine
  }

  private fun getTopInset(): Int {
    // It looks better when we place the scroll position a little bit above the text (by top inset).
    // But in the case of Ctrl+L, the screen should be scrolled to hide the previous lines.
    // When both shell integration and 'showSeparatorsBetweenBlocks' are enabled,
    // it works fine because there is a small empty space above the block.
    // But otherwise, there is a previous line.
    // So, we need to use 0 inset in this case to not show the part of the previous line.
    return if (sessionModel.terminalState.value.isShellIntegrationEnabled &&
               BlockTerminalOptions.getInstance().showSeparatorsBetweenBlocks) {
      JBUI.scale(TerminalUi.blockTopInset)
    }
    else 0
  }
}