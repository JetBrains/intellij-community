// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.host

import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.WebViewEditCommand
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.KeyEvent

/**
 * Backend-owned routing policy for WebView edit shortcuts that also exist as IDE actions.
 *
 * [SwingWebViewHostPanel.skipKeyEventDispatcher] evaluates this only while focus is inside the host.
 * The Swing panel acts as an IDE-dispatch gate; the backend decides whether the original native
 * event path is enough or whether an explicit platform edit command must be issued.
 */
@ApiStatus.Internal
enum class WebViewEditShortcutPolicy {
  /**
   * The Swing host does not intercept WebView edit shortcuts.
   *
   * Use this when the backend already gets browser-first shortcut handling, or when there is no
   * backend-specific WebView edit shortcut path.
   */
  NONE,

  /**
   * Keep IDE actions from consuming WebView edit shortcuts without invoking native peer code.
   *
   * The original key event continues through the backend's normal native/browser event path.
   */
  BYPASS_IDE_DISPATCHER,

  /**
   * Keep IDE actions from consuming WebView edit shortcuts and ask the native peer to run the
   * platform edit command explicitly.
   */
  HANDLE_IN_NATIVE_PEER,
}

@ApiStatus.Internal
interface NativeWebViewHostPeer {
  fun attach(host: Component): Boolean
  fun detach()
  fun scheduleFrameUpdate(host: Component)
  fun hasNonEmptyNativeBounds(host: Component): Boolean = SwingWebViewHostPanel.hasNonEmptyClippedBounds(host)
  fun updateVisibility(host: Component, hidden: Boolean)
  fun requestFocus()
  fun clearFocus()

  /**
   * Policy used by the Swing host for edit shortcuts while this peer owns WebView focus.
   *
   * Keeping the policy on the peer avoids OS branching in [SwingWebViewHostPanel] and makes each
   * backend declare whether it is browser-first, dispatcher-bypass, or native-command based.
   */
  val editShortcutPolicy: WebViewEditShortcutPolicy
    get() = WebViewEditShortcutPolicy.NONE

  /**
   * Called only for [WebViewEditShortcutPolicy.HANDLE_IN_NATIVE_PEER] after an IDE keymap shortcut
   * has matched a WebView edit command while focus is inside the host.
   *
   * Return `true` when the native peer accepted the event and Swing should consume it. Implementations
   * normally handle only `KEY_PRESSED`; release/typed events do not represent a platform edit command.
   */
  fun handleWebViewShortcut(event: KeyEvent, command: WebViewEditCommand): Boolean = false

  /**
   * Called when Swing focus moves outside the host. Backends may need a platform-specific
   * transfer that differs from clearing native focus completely.
   */
  fun clearFocusForSwingFocusTransfer() {
  }
}
