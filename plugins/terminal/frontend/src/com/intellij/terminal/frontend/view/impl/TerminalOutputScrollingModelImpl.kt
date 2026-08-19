// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.EditorScrollableIncrementProvider
import com.intellij.openapi.editor.impl.view.VisualLinesIterator
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import org.jetbrains.plugins.terminal.block.ui.doWithoutScrollingAnimation
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.SwingConstants
import kotlin.math.max

/**
 * Manages the vertical scroll offset of the terminal output:
 * 1. Adjusts it to follow the cursor offset or last non-blank line (depending on what is located lower)
 * if the user does not modify the scrolling position manually.
 * 2. Provides an ability to scroll to cursor: [updateScrollPosition]
 * 3. Provides an ability to scroll to [scrollByLines] and [scrollByPages] with correct snapping to the terminal lines
 * to not leave the viewport clipped by the top of the screen.
 * 4. When animated scrolling is enabled ([com.intellij.ui.scroll.MouseWheelSmoothScroll]), it ensures that
 * mouse wheel scrolling snaps to the terminal lines to not leave the viewport clipped by the top of the screen.
 *
 * Lifecycle is bound to the provided Coroutine Scope.
 */
@ApiStatus.Internal
class TerminalOutputScrollingModelImpl(
  private val editor: EditorImpl,
  private val outputModel: TerminalOutputModel,
  private val sessionModel: TerminalSessionModel,
  coroutineScope: CoroutineScope,
) : TerminalOutputScrollingModel {
  /** When true, the viewport sticks to the bottom and follows the cursor / last non-blank line as output arrives. */
  private var shouldScrollToCursor: Boolean = true
    set(value) {
      if (field != value) {
        LOG.trace { "shouldScrollToCursor: $field -> $value" }
      }
      field = value
    }

  /** The state of the output model already processed by this class, whether or not a scroll was performed for it. */
  private val appliedOutputModelState = MutableStateFlow(getCurrentOutputModelState())

  private val lifetimeDisposable = coroutineScope.asDisposable()

  init {
    // The platform tries to keep the caret's pixel position on every document change, which would randomly scroll the
    // viewport as terminal output streams in. Disable it: this model is the sole authority over the scroll offset.
    editor.putUserData(EditorImpl.DISABLE_CARET_POSITION_KEEPING, true)

    // Make the platform's wheel/scrollbar scrolling come to rest on a whole grid line (accounting for block insets),
    // instead of the editor's default fixed-line-height increment.
    editor.scrollableIncrementProvider = LineSnappingIncrementProvider()

    listenOutputModelChanges(coroutineScope.childScope("outputModelChanges")) {
      if (shouldScrollToCursor) {
        LOG.trace { "Updating scroll position because the output content changed" }
        updateScrollPosition(outputModel.cursorOffset)
      }
      else {
        appliedOutputModelState.value = getCurrentOutputModelState()
      }
    }

    // Manage the follow state from the visible area. This is the only place that reacts to thumb drag / scrollbar click.
    editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener { e ->
      val newRect = e.newRectangle
      val endY = newRect.y + newRect.height
      if (endY >= editor.contentSize.height) {
        // The viewport reached the very bottom (by any means: wheel, thumb drag, action, follow): stick again.
        shouldScrollToCursor = true
      }
      else {
        val oldRect = e.oldRectangle
        val scrolledUp = oldRect != null && newRect.y < oldRect.y
        // Only a *synchronous* user gesture stops following here (a real MouseEvent is being dispatched):
        // thumb drag, scrollbar click, or the pixel-precise wheel.
        if (scrolledUp && IdeEventQueue.getInstance().trueCurrentEvent is MouseEvent) {
          shouldScrollToCursor = false
        }
      }
    }, lifetimeDisposable)

    val scrollPane = editor.scrollPane
    val wheelListener = MouseWheelListener { e -> observeMouseWheel(e) }
    scrollPane.addMouseWheelListener(wheelListener)
    Disposer.register(lifetimeDisposable) {
      scrollPane.removeMouseWheelListener(wheelListener)
    }
  }

  @RequiresEdt
  override fun scrollToCursor(force: Boolean) {
    if (force) {
      shouldScrollToCursor = true
    }
    if (shouldScrollToCursor) {
      LOG.trace { "Updating scroll position because scrollToCursor(force=$force) was requested" }
      updateScrollPosition(outputModel.cursorOffset)
    }
  }

  @RequiresEdt
  override fun scrollByLines(lines: Int) {
    val maxOffset = maxScrollOffset()
    // The top visual line shown when scrolled all the way down. Reaching it means "at the bottom" -> resume following.
    val lastTopLine = editor.yToVisualLine(maxOffset)
    val currentTopLine = editor.yToVisualLine(editor.scrollingModel.verticalScrollOffset).coerceIn(0, lastTopLine)
    val targetLine = (currentTopLine + lines).coerceIn(0, lastTopLine)

    // Snap to the target line's top. At the extremes, include the surrounding inset in the same step: scrolling to the
    // first line reveals the top inset (offset 0), and scrolling to the last screen line reveals the bottom inset (the
    // very bottom) and resumes following. So the insets are never a separate scroll step.
    val targetOffset = when {
      targetLine >= lastTopLine -> maxOffset
      targetLine <= 0 -> 0
      else -> editor.visualLineToY(targetLine)
    }
    LOG.trace {
      "scrollByLines($lines): currentTopLine=$currentTopLine -> targetLine=$targetLine (lastTopLine=$lastTopLine), " +
      "currentOffset=${editor.scrollingModel.verticalScrollOffset} -> targetOffset=$targetOffset (maxOffset=$maxOffset)"
    }
    updateFollowState(targetOffset)
    // Reuse the editor's own scrolling animation (governed by EditorSettings.isAnimatedScrolling). Sub-line/single-line
    // steps are instant; multi-line pages ease. In tests the editor isn't showing, so this is always instant.
    editor.scrollingModel.scrollVertically(targetOffset)
  }

  @RequiresEdt
  override fun scrollByPages(pages: Int) {
    val pageLines = (editor.scrollingModel.visibleArea.height / editor.lineHeight).coerceAtLeast(1)
    scrollByLines(pageLines * pages)
  }

  /**
   * The visible area of the terminal output is bound to the screen area - the last output lines that fit into the screen height.
   * But given that we have blocks with additional vertical insets, the same number of lines may require more height than we have.
   * The terminal should show the first screen line at the top of the viewport.
   * But if we follow this rule, the line with the cursor or just the last non-blank line can occur below the viewport bounds
   * because the actual height is increased by the block insets.
   *
   * So, this method is trying to adjust the scroll offset to put the first line of the screen to the top of the viewport.
   * But if the cursor or last non-blank line becomes out of viewport, we increase the offset to make them visible.
   * The cursor is considered only if it is visible.
   */
  private fun updateScrollPosition(cursorOffset: TerminalOffset) {
    val screenRows = editor.calculateTerminalSize()?.rows ?: run {
      LOG.trace { "updateScrollPosition: skipped, terminal size is not available yet" }
      return
    }

    val screenBottomVisualLine = editor.offsetToVisualLine(editor.document.textLength, true)
    val screenTopVisualLine = max(0, screenBottomVisualLine - screenRows + 1)

    val topInset = getTopInset()
    val bottomInset = JBUI.scale(TerminalUi.blockBottomInset)

    val lastNotBlankVisualLine = findLastNotBlankVisualLine(screenTopVisualLine)
    val lastNotBlankLineBottomY = editor.visualLineToY(lastNotBlankVisualLine) + editor.lineHeight + bottomInset

    val isCursorVisible = sessionModel.terminalState.value.isCursorVisible
    val cursorVisualLine = editor.offsetToVisualLine(cursorOffset.toRelative(outputModel), true)
    val screenBottomY = if (isCursorVisible) {
      // Take the cursor into account only if it is visible.
      val cursorBottomY = editor.visualLineToY(cursorVisualLine) + editor.lineHeight + bottomInset
      max(lastNotBlankLineBottomY, cursorBottomY)
    }
    else lastNotBlankLineBottomY

    val screenTopY = editor.visualLineToY(screenTopVisualLine) - topInset
    val screenHeight = editor.scrollingModel.visibleArea.height
    val currentOffset = editor.scrollingModel.verticalScrollOffset

    val isCursorAtTop = isCursorVisible && cursorVisualLine == screenTopVisualLine
    val scrollY = if (isCursorAtTop) {
      // It is a special case: the cursor is at the top of the screen.
      // In this case we allow adjusting the position by scrolling up.
      // To support the case when the user executes "clear".
      screenTopY
    }
    else {
      // In a regular case always try to scroll to the bottom and do not scroll up
      // to not cause blinking when lines are frequently added and removed from the bottom of the screen
      maxOf(screenBottomY - screenHeight, screenTopY, currentOffset)
    }

    LOG.trace {
      "updateScrollPosition: currentOffset=$currentOffset -> scrollY=$scrollY " +
      "(${if (scrollY != currentOffset) "scrolling" else "no change"}); " +
      "cursor(visible=$isCursorVisible, line=$cursorVisualLine, atTop=$isCursorAtTop), " +
      "screen(rows=$screenRows, topLine=$screenTopVisualLine, bottomLine=$screenBottomVisualLine, height=$screenHeight), " +
      "insets(top=$topInset, bottom=$bottomInset), lastNotBlankLine=$lastNotBlankVisualLine, " +
      "y(top=$screenTopY, bottom=$screenBottomY), " +
      "candidates(bottomAligned=${screenBottomY - screenHeight}, topAligned=$screenTopY, current=$currentOffset)"
    }

    if (scrollY != currentOffset) {
      editor.doWithoutScrollingAnimation {
        editor.scrollingModel.scrollVertically(scrollY)
      }
    }

    appliedOutputModelState.value = OutputModelState(cursorOffset, outputModel.modificationStamp)
  }

  private fun observeMouseWheel(e: MouseWheelEvent) {
    if (e.isShiftDown || e.isControlDown || e.isMetaDown || e.isAltDown) return
    if (e.scrollType != MouseWheelEvent.WHEEL_UNIT_SCROLL && e.scrollType != MouseWheelEvent.WHEEL_BLOCK_SCROLL) return

    if (e.preciseWheelRotation < 0) {
      // The user scrolled up: stop following the bottom.
      shouldScrollToCursor = false
    }
  }

  /**
   * Single source of truth for the follow-the-bottom state on our own (programmatic) scrolls:
   * resume following the cursor once [offset] reaches the bottom, otherwise stop following.
   */
  private fun updateFollowState(offset: Int) {
    shouldScrollToCursor = offset >= maxScrollOffset()
  }

  private fun maxScrollOffset(): Int {
    return (editor.contentSize.height - editor.scrollingModel.visibleArea.height).coerceAtLeast(0)
  }

  private fun findLastNotBlankVisualLine(startVisualLine: Int): Int {
    var lastNotBlankVisualLine = startVisualLine

    // We need to find the last non-blank line, so it would be better to search from the end.
    // But I didn't find such an option, so let's search from the start.
    // It should not be a big deal since screen height is usually small.
    val iterator = VisualLinesIterator(editor, startVisualLine)
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

  /**
   * Output model usually fires two events on every content update: content change and cursor position change.
   * If these events are handled synchronously, the handler may see a stale cursor offset right after the content update,
   * but it will be corrected later when the cursor position change is applied.
   * To not see the "intermediate" state of the model, let's invoke [onUpdate] change asynchronously -
   * the state of the model is expected to be consistent there.
   *
   * Use the approach with [MutableStateFlow] to schedule a single [onUpdate] call
   * for a burst of output model changes that happen in the same EDT invocation.
   */
  private fun listenOutputModelChanges(coroutineScope: CoroutineScope, onUpdate: () -> Unit) {
    var counter = 0L
    val updatesFlow = MutableStateFlow(counter)

    outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        updatesFlow.value = ++counter
      }

      override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
        updatesFlow.value = ++counter
      }
    })

    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      updatesFlow
        .filter { it != 0L }  // Do not call `onUpdate()` for the initial state
        .collect {
          onUpdate()
        }
    }
  }

  @TestOnly
  suspend fun awaitEventProcessing() {
    val expectedState = getCurrentOutputModelState()
    appliedOutputModelState.first { it == expectedState }
  }

  private fun getCurrentOutputModelState(): OutputModelState {
    return OutputModelState(outputModel.cursorOffset, outputModel.modificationStamp)
  }

  private data class OutputModelState(val cursorOffset: TerminalOffset, val docStamp: Long)

  /**
   * Reports scroll increments that come to rest on the terminal's whole grid lines. Unlike the editor's default,
   * it aligns to the *actual* visual-line boundaries via [EditorImpl.yToVisualLine] / [EditorImpl.visualLineToY],
   * so the insets between command blocks that make those boundaries non-uniform are respected.
   *
   * Note that it is not taken into account on macOS because native per-pixel smooth scrolling is used there.
   * Debug [com.intellij.ui.components.JBScrollPane.JBMouseWheelListener.mouseWheelMoved] to understand all the details.
   */
  private class LineSnappingIncrementProvider : EditorScrollableIncrementProvider {
    override fun getScrollableUnitIncrement(editor: Editor, visibleRect: Rectangle, orientation: Int, direction: Int): Int {
      if (orientation != SwingConstants.VERTICAL) {
        return EditorScrollableIncrementProvider.DEFAULT.getScrollableUnitIncrement(editor, visibleRect, orientation, direction)
      }
      val y = visibleRect.y
      val line = editor.yToVisualLine(y)
      return if (direction > 0) {
        // Distance down to the next visual-line boundary (a full line when already aligned).
        (editor.visualLineToY(line + 1) - y).coerceAtLeast(0)
      }
      else {
        val lineTopY = editor.visualLineToY(line)
        if (y > lineTopY) {
          y - lineTopY  // mid-line: snap up to this line's top
        }
        else {
          y - editor.visualLineToY((line - 1).coerceAtLeast(0))  // aligned: a full line up (0 at the very top)
        }
      }
    }

    override fun getScrollableBlockIncrement(editor: Editor, visibleRect: Rectangle, orientation: Int, direction: Int): Int {
      if (orientation != SwingConstants.VERTICAL) {
        return EditorScrollableIncrementProvider.DEFAULT.getScrollableBlockIncrement(editor, visibleRect, orientation, direction)
      }
      // A page that lands on a whole line boundary: advance by the viewport height, then align to the visual line there.
      val height = visibleRect.height
      return if (direction > 0) {
        val targetY = visibleRect.y + height
        val line = editor.yToVisualLine(targetY)
        (editor.visualLineToY(line) - visibleRect.y).coerceAtLeast(0)
      }
      else {
        val targetY = (visibleRect.y - height).coerceAtLeast(0)
        val line = editor.yToVisualLine(targetY)
        val alignedY = editor.visualLineToY(line)
        (visibleRect.y - alignedY).coerceAtLeast(0)
      }
    }
  }

  companion object {
    private val LOG = logger<TerminalOutputScrollingModelImpl>()
  }
}