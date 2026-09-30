// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.awt.event.InputEvent
import javax.swing.SwingUtilities

/**
 * Moves the shell cursor to the clicked place in the command that the user types.
 * The shell gets the left or right arrow keys, as if the user pressed them.
 *
 * It handles the click, which the editor reports after its own handling of the release, for example, the removal of the selection.
 * The editor reports no click after a drag, or if a listener consumed the press or the release, for example, the mouse reporting.
 */
internal fun installPromptClickHandling(
  editor: Editor,
  outputModel: TerminalOutputModel,
  shellIntegration: TerminalShellIntegration,
  terminalInput: TerminalInput,
  parentDisposable: Disposable,
) {
  editor.addEditorMouseListener(object : EditorMouseListener {
    override fun mouseClicked(event: EditorMouseEvent) {
      val mouseEvent = event.mouseEvent
      if (event.area != EditorMouseEventArea.EDITING_AREA || !SwingUtilities.isLeftMouseButton(mouseEvent)) return
      // A click with a modifier is for the editor, for example, Shift+click extends the selection.
      if (mouseEvent.modifiersEx and CLICK_MODIFIERS_MASK != 0) return
      // The click selected text, for example, a double click selects a word.
      if (editor.selectionModel.hasSelection()) return

      val block = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return
      val clickOffset = outputModel.startOffset + event.clickedCharacterOffset().toLong()
      val move = calculateCursorMoveToClick(outputModel, block, clickOffset) ?: return
      repeat(move.keyCount) {
        when (move.direction) {
          TerminalCursorMove.Direction.LEFT -> terminalInput.sendLeft()
          TerminalCursorMove.Direction.RIGHT -> terminalInput.sendRight()
        }
      }
    }
  }, parentDisposable)
}

/**
 * Returns the offset of the character under the pointer, as for a block cursor.
 * [EditorMouseEvent.getOffset] is the nearest character boundary, so it is after the character for a click on its right half.
 * The zero-width code points after a character, for example, a combining mark, belong to this character.
 */
private fun EditorMouseEvent.clickedCharacterOffset(): Int {
  val text = editor.document.immutableCharSequence
  val isLeftOfBoundary = isOverText && visualPosition.column > 0 &&
                         mouseEvent.x < editor.visualPositionToPoint2D(visualPosition).x
  var characterOffset = if (isLeftOfBoundary) Character.offsetByCodePoints(text, offset, -1) else offset
  while (characterOffset > 0 && characterOffset < text.length && isZeroWidth(Character.codePointAt(text, characterOffset))) {
    characterOffset = Character.offsetByCodePoints(text, characterOffset, -1)
  }
  return characterOffset
}

/** The arrow keys that move the shell cursor: [keyCount] presses of the [direction] key. */
@ApiStatus.Internal
@VisibleForTesting
data class TerminalCursorMove(val direction: Direction, val keyCount: Int) {
  enum class Direction { LEFT, RIGHT }
}

/**
 * Returns the move of the cursor from [TerminalOutputModel.cursorOffset] to the clicked character at [clickOffset]
 * in the typed command of [block].
 * Returns null if the cursor is already there, or if the command, the cursor or the click is out of reach.
 *
 * The move stays in the line where the command starts, because the continuation lines start with an unknown prompt.
 * A click in the prompt moves the cursor to the command start.
 * One key press moves the cursor over one character and the zero-width code points after it, as readline and zle do.
 */
@ApiStatus.Internal
@VisibleForTesting
fun calculateCursorMoveToClick(model: TerminalOutputModel, block: TerminalCommandBlock, clickOffset: TerminalOffset): TerminalCursorMove? {
  val commandStart = block.commandStartOffset ?: return null
  // The command runs, so the shell does not edit it.
  if (block.outputStartOffset != null) return null
  val cursor = model.cursorOffset
  if (commandStart < model.startOffset || commandStart > cursor) return null
  val line = model.getLineByOffset(commandStart)
  if (model.getLineByOffset(cursor) != line || model.getLineByOffset(clickOffset) != line) return null

  // The indexes below are relative to the command start.
  val commandText = model.getText(commandStart, model.getEndOfLine(line)).toString()
  val cursorIndex = (cursor - commandStart).toInt()
  val targetIndex = (clickOffset - commandStart).coerceAtLeast(0L).toInt()
  return when {
    targetIndex < cursorIndex -> {
      TerminalCursorMove(TerminalCursorMove.Direction.LEFT, commandText.keyCount(targetIndex, cursorIndex))
    }
    targetIndex > cursorIndex -> {
      TerminalCursorMove(TerminalCursorMove.Direction.RIGHT, commandText.keyCount(cursorIndex, targetIndex))
    }
    else -> null
  }
}

private fun String.keyCount(start: Int, end: Int): Int {
  return substring(start, end).codePoints().filter { !isZeroWidth(it) }.count().toInt()
}

/** Combining marks and format characters, for example, the zero width joiner. */
private fun isZeroWidth(codePoint: Int): Boolean {
  val type = Character.getType(codePoint)
  return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() || type == Character.FORMAT.toInt()
}

private const val CLICK_MODIFIERS_MASK: Int =
  InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
