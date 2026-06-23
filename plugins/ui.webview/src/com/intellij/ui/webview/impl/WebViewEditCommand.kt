// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.KeyStroke

/**
 * The shared catalog of WebView-owned edit shortcuts — the single source of truth for every OS backend:
 * native-host shortcut policy and the Windows accelerator-key filter (which shortcuts the browser keeps).
 * Each command is the IDE action id whose keymap shortcut triggers it. Open class (not an `enum`) so the
 * catalog can be extended.
 */
@ApiStatus.Internal
open class WebViewEditCommand(val ideActionId: String) {
  companion object {
    val COPY: WebViewEditCommand = WebViewEditCommand("\$Copy")
    val PASTE: WebViewEditCommand = WebViewEditCommand("\$Paste")
    val CUT: WebViewEditCommand = WebViewEditCommand("\$Cut")
    val SELECT_ALL: WebViewEditCommand = WebViewEditCommand("\$SelectAll")
    val UNDO: WebViewEditCommand = WebViewEditCommand("\$Undo")
    val REDO: WebViewEditCommand = WebViewEditCommand("\$Redo")

    /** All built-in edit shortcuts, in a stable order. */
    val DEFAULTS: List<WebViewEditCommand> = listOf(COPY, PASTE, CUT, SELECT_ALL, UNDO, REDO)

    /**
     * Returns the [enabled] command whose IDE-action keymap shortcut matches the keystroke
     * (`keyCode` + `modifiersEx`, AWT `InputEvent` masks), or `null` if none. Used by native-host
     * shortcut policies and the Windows accelerator filter.
     * Returns `null` if there is no [com.intellij.openapi.application.Application] (no keymap available).
     */
    fun matchingCommand(keyCode: Int, modifiersEx: Int, enabled: Collection<WebViewEditCommand>): WebViewEditCommand? {
      if (ApplicationManager.getApplication() == null) return null
      val target = KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, modifiersEx), null)
      val actionManager = ActionManager.getInstance()
      return enabled.firstOrNull { command ->
        val action = actionManager.getAction(command.ideActionId) ?: return@firstOrNull false
        action.shortcutSet.shortcuts.any { it == target }
      }
    }
  }
}
