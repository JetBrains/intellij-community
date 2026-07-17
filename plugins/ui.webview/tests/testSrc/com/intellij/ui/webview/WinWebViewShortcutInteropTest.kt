// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.impl.WebViewShortcutRouter
import com.intellij.ui.webview.impl.WebViewShortcutRouting
import com.intellij.ui.webview.impl.windows.WinWebViewShortcutInterop
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Panel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class WinWebViewShortcutInteropTest {
  @Test
  fun `creates ctrl letter key pressed event`() {
    val event = WinWebViewShortcutInterop.createKeyEvent(
      Panel(),
      WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN,
      'S'.code,
      WinWebViewShortcutInterop.MODIFIER_CONTROL,
      0,
    )

    assertNotNull(event)
    assertEquals(KeyEvent.KEY_PRESSED, event!!.id)
    assertEquals(KeyEvent.VK_S, event.keyCode)
    assertEquals(InputEvent.CTRL_DOWN_MASK, event.modifiersEx)
    assertEquals(KeyEvent.CHAR_UNDEFINED, event.keyChar)
  }

  @Test
  fun `maps windows oem and navigation virtual keys to java key codes`() {
    val minus = WinWebViewShortcutInterop.createKeyEvent(
      Panel(),
      WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN,
      0xBD,
      WinWebViewShortcutInterop.MODIFIER_CONTROL,
      0,
    )
    val delete = WinWebViewShortcutInterop.createKeyEvent(
      Panel(),
      WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN,
      0x2E,
      WinWebViewShortcutInterop.MODIFIER_CONTROL,
      0,
    )

    assertEquals(KeyEvent.VK_MINUS, minus!!.keyCode)
    assertEquals(KeyEvent.VK_DELETE, delete!!.keyCode)
  }

  @Test
  fun `detects shortcut candidates without taking plain typing`() {
    assertTrue(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK))
    assertTrue(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK))
    assertTrue(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_ESCAPE, 0))

    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_A, 0))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
    assertFalse(WinWebViewShortcutInterop.isShortcutCandidate(KeyEvent.VK_DELETE, InputEvent.CTRL_DOWN_MASK))
  }

  @Test
  fun `routes shift and ctrl gestures to ide without consuming browser handling`() {
    val shiftPress = createWindowsKeyEvent(VK_SHIFT, WinWebViewShortcutInterop.MODIFIER_SHIFT)
    val shiftRelease = createWindowsKeyEvent(
      virtualKey = VK_SHIFT,
      modifierFlags = 0,
      keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_UP,
    )
    val ctrlPress = createWindowsKeyEvent(VK_CONTROL, WinWebViewShortcutInterop.MODIFIER_CONTROL)
    val ctrlRelease = createWindowsKeyEvent(
      virtualKey = VK_CONTROL,
      modifierFlags = 0,
      keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_UP,
    )

    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_KEEP_BROWSER_HANDLING,
      WebViewShortcutRouter.route(shiftPress),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_KEEP_BROWSER_HANDLING,
      WebViewShortcutRouter.route(shiftRelease),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_KEEP_BROWSER_HANDLING,
      WebViewShortcutRouter.route(ctrlPress),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_KEEP_BROWSER_HANDLING,
      WebViewShortcutRouter.route(ctrlRelease),
    )
  }

  @Test
  fun `routes ide accelerators to ide and consumes browser handling`() {
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      WebViewShortcutRouter.route(createWindowsKeyEvent('S'.code, WinWebViewShortcutInterop.MODIFIER_CONTROL)),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      WebViewShortcutRouter.route(createWindowsKeyEvent(VK_F4, 0)),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      WebViewShortcutRouter.route(createWindowsKeyEvent(VK_ESCAPE, 0)),
    )
  }

  @Test
  fun `routes plain typing to browser only`() {
    val plainA = createWindowsKeyEvent('A'.code, 0)

    assertEquals(WebViewShortcutRouting.BROWSER_ONLY, WebViewShortcutRouter.route(plainA))
  }

  @Test
  fun `routes browser text navigation to browser only`() {
    val ctrlLeft = createWindowsKeyEvent(VK_LEFT, WinWebViewShortcutInterop.MODIFIER_CONTROL)
    val ctrlShiftRight = createWindowsKeyEvent(VK_RIGHT, WinWebViewShortcutInterop.MODIFIER_CONTROL or WinWebViewShortcutInterop.MODIFIER_SHIFT)

    assertEquals(WebViewShortcutRouting.BROWSER_ONLY, WebViewShortcutRouter.route(ctrlLeft))
    assertEquals(WebViewShortcutRouting.BROWSER_ONLY, WebViewShortcutRouter.route(ctrlShiftRight))
  }

  @Test
  fun `routes active keymap system shortcuts to ide before native fallback`() {
    val activeShortcuts = activeKeymapShortcuts(
      KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK),
      KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.ALT_DOWN_MASK),
    )

    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      routeWindowsKeyEvent(
        virtualKey = VK_F1,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
        keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_DOWN,
        activeKeymapShortcuts = activeShortcuts,
      ),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      routeWindowsKeyEvent(
        virtualKey = VK_F10,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
        keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_DOWN,
        activeKeymapShortcuts = activeShortcuts,
      ),
    )
  }

  @Test
  fun `leaves unbound system shortcuts for native fallback`() {
    val activeShortcuts = activeKeymapShortcuts(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK))

    assertEquals(
      WebViewShortcutRouting.BROWSER_ONLY,
      routeWindowsKeyEvent(
        virtualKey = VK_F4,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
        keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_DOWN,
        activeKeymapShortcuts = activeShortcuts,
      ),
    )
  }

  @Test
  fun `system key release follows pressed ownership`() {
    val state = WinWebViewShortcutInterop.SystemKeyRoutingState()
    val activeShortcuts = activeKeymapShortcuts(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK))

    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      routeWindowsKeyEvent(
        virtualKey = VK_F1,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
        keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_DOWN,
        activeKeymapShortcuts = activeShortcuts,
        state = state,
      ),
    )
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      routeWindowsKeyEvent(
        virtualKey = VK_F1,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
        keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_UP,
        activeKeymapShortcuts = activeShortcuts,
        state = state,
      ),
    )
    assertEquals(
      WebViewShortcutRouting.BROWSER_ONLY,
      routeWindowsKeyEvent(
        virtualKey = VK_F1,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_ALT,
        keyEventKind = WinWebViewShortcutInterop.KEY_EVENT_KIND_SYSTEM_KEY_UP,
        activeKeymapShortcuts = activeShortcuts,
        state = state,
      ),
    )
  }

  @Test
  fun `ordinary key routing keeps existing shortcut behavior`() {
    assertEquals(
      WebViewShortcutRouting.FORWARD_TO_IDE_CONSUME_BROWSER_HANDLING,
      routeWindowsKeyEvent(
        virtualKey = 'S'.code,
        modifierFlags = WinWebViewShortcutInterop.MODIFIER_CONTROL,
        activeKeymapShortcuts = activeKeymapShortcuts(),
      ),
    )
  }

  @Test
  fun `preserves left and right modifier key locations`() {
    assertEquals(
      KeyEvent.KEY_LOCATION_LEFT,
      createWindowsKeyEvent(VK_LSHIFT, WinWebViewShortcutInterop.MODIFIER_SHIFT).keyLocation,
    )
    assertEquals(
      KeyEvent.KEY_LOCATION_RIGHT,
      createWindowsKeyEvent(VK_RSHIFT, WinWebViewShortcutInterop.MODIFIER_SHIFT).keyLocation,
    )
    assertEquals(
      KeyEvent.KEY_LOCATION_LEFT,
      createWindowsKeyEvent(VK_LCONTROL, WinWebViewShortcutInterop.MODIFIER_CONTROL).keyLocation,
    )
    assertEquals(
      KeyEvent.KEY_LOCATION_RIGHT,
      createWindowsKeyEvent(VK_RCONTROL, WinWebViewShortcutInterop.MODIFIER_CONTROL).keyLocation,
    )
    assertEquals(
      KeyEvent.KEY_LOCATION_LEFT,
      createWindowsKeyEvent(VK_CONTROL, WinWebViewShortcutInterop.MODIFIER_CONTROL).keyLocation,
    )
    assertEquals(
      KeyEvent.KEY_LOCATION_RIGHT,
      createWindowsKeyEvent(VK_CONTROL, WinWebViewShortcutInterop.MODIFIER_CONTROL, keyEventLParam = EXTENDED_KEY_MASK).keyLocation,
    )
  }

  @Test
  fun `ignores unknown webview2 key event kind`() {
    assertNull(
      WinWebViewShortcutInterop.createKeyEvent(
        Panel(),
        -1,
        'S'.code,
        WinWebViewShortcutInterop.MODIFIER_CONTROL,
        0,
      )
    )
  }

  private fun createWindowsKeyEvent(
    virtualKey: Int,
    modifierFlags: Int,
    keyEventKind: Int = WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN,
    keyEventLParam: Int = 0,
  ): KeyEvent {
    return checkNotNull(
      WinWebViewShortcutInterop.createKeyEvent(
        Panel(),
        keyEventKind,
        virtualKey,
        modifierFlags,
        keyEventLParam,
      )
    )
  }

  private fun routeWindowsKeyEvent(
    virtualKey: Int,
    modifierFlags: Int,
    keyEventKind: Int = WinWebViewShortcutInterop.KEY_EVENT_KIND_KEY_DOWN,
    activeKeymapShortcuts: (KeyEvent) -> Boolean,
    state: WinWebViewShortcutInterop.SystemKeyRoutingState = WinWebViewShortcutInterop.SystemKeyRoutingState(),
  ): WebViewShortcutRouting {
    val event = createWindowsKeyEvent(virtualKey, modifierFlags, keyEventKind)
    return WinWebViewShortcutInterop.routeKeyEvent(
      keyEventKind = keyEventKind,
      virtualKey = virtualKey,
      keyEvent = event,
      hasActiveKeymapShortcut = activeKeymapShortcuts,
      systemKeyRoutingState = state,
    )
  }

  private fun activeKeymapShortcuts(vararg keyStrokes: KeyStroke): (KeyEvent) -> Boolean {
    val shortcuts = keyStrokes.toSet()
    return { event -> KeyStroke.getKeyStrokeForEvent(event) in shortcuts }
  }

  private companion object {
    private const val EXTENDED_KEY_MASK: Int = 1 shl 24
    private const val VK_ESCAPE: Int = 0x1B
    private const val VK_F1: Int = 0x70
    private const val VK_F4: Int = 0x73
    private const val VK_F10: Int = 0x79
    private const val VK_SHIFT: Int = 0x10
    private const val VK_CONTROL: Int = 0x11
    private const val VK_LEFT: Int = 0x25
    private const val VK_RIGHT: Int = 0x27
    private const val VK_LSHIFT: Int = 0xA0
    private const val VK_RSHIFT: Int = 0xA1
    private const val VK_LCONTROL: Int = 0xA2
    private const val VK_RCONTROL: Int = 0xA3
  }
}
