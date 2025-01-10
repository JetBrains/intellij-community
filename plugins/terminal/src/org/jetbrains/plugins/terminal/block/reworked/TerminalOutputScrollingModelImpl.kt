// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.ui.*
import kotlin.math.max

/**
 * Manages the vertical scroll offset of the terminal output:
 * 1. Adjusts it to follow the cursor offset if the user does not modify scrolling position manually.
 * 2. Provides an ability to scroll to cursor forcefully: [scrollToCursor]
 *
 * Lifecycle is bound to the provided Coroutine Scope.
 */
internal class TerminalOutputScrollingModelImpl(
  private val outputModel: TerminalOutputModel,
  coroutineScope: CoroutineScope,
) : TerminalOutputScrollingModel {
  private val editor: EditorEx
    get() = outputModel.editor

  private var shouldScrollToCursor: Boolean = true

  init {
    coroutineScope.launch(Dispatchers.EDT) {
      outputModel.cursorOffsetState.collect { offset ->
        if (shouldScrollToCursor) {
          scrollToCursor(offset)
        }
      }
    }

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
        else if (e.oldRectangle.y != e.newRectangle.y) {
          // The user has changed the scroll offset, so we should stop following the cursor.
          shouldScrollToCursor = false
        }
      }
    }, coroutineScope.asDisposable())
  }

  @RequiresEdt
  override fun scrollToCursor() {
    shouldScrollToCursor = true
    scrollToCursor(outputModel.cursorOffsetState.value)
  }

  /**
   * The visible area of the terminal output is bound to the screen area - the last output lines that fit into the screen height.
   * But given that we have blocks with additional vertical insets, the same number of lines may require more height than we have.
   * The terminal should show the first screen line at the top of the viewport.
   * But if we follow this rule, the line with cursor can occur below the viewport bounds
   * because actual height is increased by the block insets.
   *
   * So, this method is trying to adjust the scroll offset to put the first line of the screen to the top of the viewport.
   * But if the cursor becomes out of viewport, we increase the offset to make the cursor visible.
   */
  private fun scrollToCursor(offset: Int) {
    val screenRows = editor.calculateTerminalSize()?.rows ?: return

    val screenBottomVisualLine = editor.offsetToVisualLine(editor.document.textLength, true)
    val screenTopVisualLine = max(0, screenBottomVisualLine - screenRows + 1)

    val topInset = JBUI.scale(TerminalUi.blockTopInset)
    val bottomInset = JBUI.scale(TerminalUi.blockBottomInset)

    val cursorBottomY = editor.offsetToXY(offset).y + editor.lineHeight + bottomInset
    val screenTopY = editor.visualLineToY(screenTopVisualLine) - topInset
    val screenHeight = editor.scrollingModel.visibleArea.height

    val scrollY = max(cursorBottomY - screenHeight, screenTopY)

    editor.doTerminalOutputScrollChangingAction {
      editor.doWithoutScrollingAnimation {
        editor.scrollingModel.scrollVertically(scrollY)
      }
    }
  }
}