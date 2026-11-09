// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.action

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xray.frontend.api.XRayMode
import com.intellij.xray.frontend.api.editorTracking.XRayTimings
import com.intellij.xray.frontend.impl.service.XRayModeService
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent

/**
 * Drives the X-Ray "peek" gesture started from the `ToggleXRayMode` shortcut (Ctrl+Shift+X):
 *
 * - **Tap** (press and release within [XRayTimings.KEY_AUTOREPEAT_WINDOW_MS]) cycles the persistent X-Ray mode.
 * - **Hold** (keep pressed past [XRayTimings.PEEK_HOLD_DELAY_MS]) enters a temporary BASIC peek that reverts
 *   as soon as a modifier is released.
 * - **Re-press X while peeking** flips the peek between BASIC and EXTENDED.
 */
internal class XRayPeekGesture private constructor(
  private val editor: Editor,
  private val onFinished: () -> Unit,
) {
  companion object {
    /** Holds the gesture currently running for a given editor, so only one runs at a time (no state on the action). */
    private val ACTIVE_GESTURE: Key<XRayPeekGesture> = Key.create("xray.peek.gesture.active")

    /** Starts a peek gesture for [editor], unless one is already running there. */
    @RequiresEdt
    fun startIfNoneActive(editor: Editor) {
      ThreadingAssertions.assertEventDispatchThread()
      if (editor.getUserData(ACTIVE_GESTURE) != null) return
      val gesture = XRayPeekGesture(editor) { editor.putUserData(ACTIVE_GESTURE, null) }
      editor.putUserData(ACTIVE_GESTURE, gesture)
      gesture.start()
    }
  }

  private sealed interface State {
    object Listening : State
    data class Peeking(val originalMode: XRayMode) : State
  }
  private val gestureDisposable = Disposer.newDisposable("XRayPeekGesture")

  private var state: State = State.Listening
  private var terminated = false

  /** Set when X is released during a peek; a following re-press then flips BASIC <-> EXTENDED. */
  private var awaitingFlipRePress = false

  private val holdAlarm =
    SingleAlarm.singleEdtAlarm(XRayTimings.PEEK_HOLD_DELAY_MS, gestureDisposable) { onHoldThresholdReached() }
  private val xReleaseAlarm =
    SingleAlarm.singleEdtAlarm(XRayTimings.KEY_AUTOREPEAT_WINDOW_MS, gestureDisposable) { onXReleaseElapsed() }

  private fun start() {
    ThreadingAssertions.assertEventDispatchThread()
    val service = XRayModeService.getInstance()

    Disposer.register(service, gestureDisposable)
    Disposer.register(gestureDisposable, Disposable { onTerminated() })
    IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.NonLockedEventDispatcher { e -> onEvent(e) }, gestureDisposable)
    holdAlarm.request()
  }

  private fun onEvent(event: AWTEvent): Boolean {
    return !terminated && when (event) {
      is WindowEvent -> {
        if (event.id == WindowEvent.WINDOW_LOST_FOCUS) cancel()
        false
      }
      is KeyEvent -> onKeyEvent(event)
      else -> false
    }
  }

  /** Returns true if the event was consumed; only stray 'X' events are. */
  private fun onKeyEvent(event: KeyEvent): Boolean {
    // Releasing a modifier ends an active peek - but never consume it, other handlers still need it.
    if (event.id == KeyEvent.KEY_RELEASED &&
        (event.keyCode == KeyEvent.VK_CONTROL || event.keyCode == KeyEvent.VK_SHIFT) &&
        state is State.Peeking
    ) {
      finish()
      return false
    }

    if (!isXKeyEvent(event)) return false

    when (event.id) {
      KeyEvent.KEY_PRESSED -> {
        xReleaseAlarm.cancel()
        // A re-press of the full shortcut while peeking flips BASIC <-> EXTENDED.
        if (state is State.Peeking && event.isControlDown && event.isShiftDown && awaitingFlipRePress) {
          awaitingFlipRePress = false
          flipMode()
        }
      }
      KeyEvent.KEY_RELEASED -> xReleaseAlarm.cancelAndRequest()
    }
    return true
  }

  private fun isXKeyEvent(event: KeyEvent): Boolean {
    return event.keyCode == KeyEvent.VK_X ||
           (event.id == KeyEvent.KEY_TYPED && (event.keyChar == 'x' || event.keyChar == 'X'))
  }

  private fun onHoldThresholdReached() {
    val service = XRayModeService.getInstance()

    if (state !is State.Listening) return
    val original = service.getXRayModeState()

    service.beginPeek()

    setMode(XRayMode.BASIC)
    state = State.Peeking(original)
  }

  /** Fires once X has stayed released past the auto-repeat window, i.e. it was a real release rather than repeat. */
  private fun onXReleaseElapsed() {
    when (state) {
      State.Listening -> finish()
      is State.Peeking -> awaitingFlipRePress = true
    }
  }

  /** Deliberate end of the gesture: revert a peek, or cycle the persistent mode if it was just a tap. */
  private fun finish() {
    if (terminated) return
    val current = state
    Disposer.dispose(gestureDisposable)

    val service = XRayModeService.getInstance()
    when (current) {
      is State.Peeking -> revert(current.originalMode)
      State.Listening -> {
        service.cycleXRayMode(editor)
        refreshToolbars()
      }
    }
  }

  /** Abandon the gesture (e.g. the window lost focus): revert a peek, but never treat it as a tap. */
  private fun cancel() {
    if (terminated) return
    val current = state
    Disposer.dispose(gestureDisposable)
    if (current is State.Peeking) revert(current.originalMode)
  }

  /** Runs exactly once when [gestureDisposable] is disposed, whatever triggered it. */
  private fun onTerminated() {
    terminated = true
    onFinished()
  }

  private fun revert(original: XRayMode) {
    val service = XRayModeService.getInstance()

    service.endPeek()
    setMode(original)
  }

  private fun setMode(mode: XRayMode) {
    val service = XRayModeService.getInstance()

    service.setXRayMode(mode, editor)
    refreshToolbars()
  }

  private fun flipMode() {
    val service = XRayModeService.getInstance()

    when (service.getXRayModeState()) {
      XRayMode.BASIC -> setMode(XRayMode.EXTENDED)
      XRayMode.EXTENDED, XRayMode.DISABLED -> setMode(XRayMode.BASIC)
    }
  }

  private fun refreshToolbars() {
    ActionToolbarImpl.updateAllToolbarsImmediately(false)
  }
}
