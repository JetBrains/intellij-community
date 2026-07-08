// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal enum class WebViewShortcutRouting {
  BROWSER_ONLY,
  FORWARD_TO_IDE_KEEP_BROWSER_HANDLING,
  FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
}

internal object WebViewShortcutRouter {
  // Ownership model:
  // - browser/WebView keeps normal input, text editing, selection, IME, and browser text navigation;
  // - IDE receives true IDE accelerators and bare modifier gestures;
  // - native backends may forward OS-level system keys before this synthetic accelerator path sees them.
  fun route(event: KeyEvent): WebViewShortcutRouting {
    if (isModifierGestureCandidate(event)) {
      return WebViewShortcutRouting.FORWARD_TO_IDE_KEEP_BROWSER_HANDLING
    }
    if (!isShortcutCandidate(event.keyCode, event.modifiersEx)) {
      return WebViewShortcutRouting.BROWSER_ONLY
    }
    return WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING
  }

  fun isShortcutCandidate(keyCode: Int, modifiersEx: Int): Boolean {
    if (keyCode == KeyEvent.VK_UNDEFINED || isModifierKey(keyCode)) return false
    if (isBrowserEditingShortcut(keyCode, modifiersEx) || isBrowserTextNavigationShortcut(keyCode, modifiersEx)) return false

    val commandModifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
    return modifiersEx and commandModifiers != 0 || keyCode in KeyEvent.VK_F1..KeyEvent.VK_F24 || keyCode == KeyEvent.VK_ESCAPE
  }

  private fun isModifierGestureCandidate(event: KeyEvent): Boolean {
    // Keep this list local until ModifierKeyDoubleClickHandler exposes a query for active bare modifier
    // double-click keys. That handler is the IDE source of truth for the gesture state machine; WebView
    // only needs to forward Shift/Ctrl transitions so the handler can see them while native WebViews own focus.
    return (event.id == KeyEvent.KEY_PRESSED || event.id == KeyEvent.KEY_RELEASED) &&
           (event.keyCode == KeyEvent.VK_SHIFT || event.keyCode == KeyEvent.VK_CONTROL)
  }

  // A keystroke is "browser editing" when the active keymap binds it to one of the shared WebView edit
  // commands (Copy/Paste/Cut/SelectAll/Undo/Redo). Such keystrokes are kept for native browser editing
  // rather than forwarded to the IDE. Source of truth: WebViewEditCommand (shared across all OS backends).
  private fun isBrowserEditingShortcut(keyCode: Int, modifiersEx: Int): Boolean {
    return WebViewEditCommand.matchingCommand(keyCode, modifiersEx, WebViewEditCommand.DEFAULTS) != null
  }

  // Keep WebView-owned caret movement/deletion in the browser editor. These shortcuts are not IDE
  // accelerators while focus is inside editable WebView content; consuming them breaks native input fields.
  private fun isBrowserTextNavigationShortcut(keyCode: Int, modifiersEx: Int): Boolean {
    val nonShiftModifiers = modifiersEx and (InputEvent.CTRL_DOWN_MASK or
                                             InputEvent.ALT_DOWN_MASK or
                                             InputEvent.META_DOWN_MASK or
                                             InputEvent.ALT_GRAPH_DOWN_MASK)
    if (nonShiftModifiers != InputEvent.CTRL_DOWN_MASK) return false
    return keyCode == KeyEvent.VK_LEFT ||
           keyCode == KeyEvent.VK_RIGHT ||
           keyCode == KeyEvent.VK_HOME ||
           keyCode == KeyEvent.VK_END ||
           keyCode == KeyEvent.VK_BACK_SPACE ||
           keyCode == KeyEvent.VK_DELETE
  }

  private fun isModifierKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.VK_SHIFT ||
           keyCode == KeyEvent.VK_CONTROL ||
           keyCode == KeyEvent.VK_ALT ||
           keyCode == KeyEvent.VK_META ||
           keyCode == KeyEvent.VK_ALT_GRAPH
  }
}
