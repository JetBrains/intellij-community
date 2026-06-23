// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

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
}
