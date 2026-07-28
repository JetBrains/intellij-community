package com.intellij.terminal.frontend.view.inlineCompletion

import com.intellij.openapi.application.EDT
import com.intellij.terminal.frontend.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.view.TerminalOffset
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal class TerminalInlineCompletionInputListener(
  private val controller: TerminalInlineCompletionController,
) {
  fun install(terminalView: TerminalView, coroutineScope: CoroutineScope) {
    coroutineScope.launch(Dispatchers.EDT) {
      terminalView.keyEventsFlow.collect { event ->
        when (event.awtEvent.id) {
          KeyEvent.KEY_TYPED -> handleKeyTyped(event.awtEvent, event.cursorOffset)
          KeyEvent.KEY_PRESSED -> handleKeyPressed(event.awtEvent, event.cursorOffset)
        }
      }
    }
  }

  private fun handleKeyTyped(event: KeyEvent, cursorOffset: TerminalOffset) {
    if (!Character.isISOControl(event.keyChar)) {
      controller.handleInput(TerminalInlineCompletionInputEvent.Typing(event.keyChar), cursorOffset)
    }
  }

  private fun handleKeyPressed(event: KeyEvent, cursorOffset: TerminalOffset) {
    when (event.keyCode) {
      KeyEvent.VK_BACK_SPACE -> if (event.hasNoModifiers()) {
        controller.handleInput(TerminalInlineCompletionInputEvent.Backspace, cursorOffset)
      }
      KeyEvent.VK_TAB,
      KeyEvent.VK_LEFT,
      KeyEvent.VK_RIGHT,
      KeyEvent.VK_UP,
      KeyEvent.VK_DOWN,
      KeyEvent.VK_HOME,
      KeyEvent.VK_END -> {
        controller.handleInput(TerminalInlineCompletionInputEvent.Invalidate, cursorOffset)
      }
    }
  }

  private fun KeyEvent.hasNoModifiers(): Boolean {
    val nonTypingModifiers = InputEvent.ALT_DOWN_MASK or
                              InputEvent.ALT_GRAPH_DOWN_MASK or
                              InputEvent.CTRL_DOWN_MASK or
                              InputEvent.SHIFT_DOWN_MASK
    return modifiersEx and nonTypingModifiers == 0
  }
}

internal sealed interface TerminalInlineCompletionInputEvent {
  data class Typing(val char: Char) : TerminalInlineCompletionInputEvent
  data object Backspace : TerminalInlineCompletionInputEvent
  data object Invalidate : TerminalInlineCompletionInputEvent
}
