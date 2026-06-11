// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal object WinWebViewShortcutInterop {
  internal const val KEY_EVENT_KIND_KEY_DOWN: Int = 0
  internal const val KEY_EVENT_KIND_KEY_UP: Int = 1
  internal const val KEY_EVENT_KIND_SYSTEM_KEY_DOWN: Int = 2
  internal const val KEY_EVENT_KIND_SYSTEM_KEY_UP: Int = 3

  internal const val MODIFIER_SHIFT: Int = 1
  internal const val MODIFIER_CONTROL: Int = 1 shl 1
  internal const val MODIFIER_ALT: Int = 1 shl 2
  internal const val MODIFIER_META: Int = 1 shl 3

  fun handleAcceleratorKeyPressed(target: Component?, keyEventKind: Int, virtualKey: Int, modifierFlags: Int, keyEventLParam: Int): Boolean {
    if (target == null || !target.isShowing) return false

    val eventSource = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow ?: target
    val keyEvent = createKeyEvent(eventSource, keyEventKind, virtualKey, modifierFlags, keyEventLParam) ?: return false
    if (!isShortcutCandidate(keyEvent.keyCode, keyEvent.modifiersEx)) return false

    Toolkit.getDefaultToolkit().systemEventQueue.postEvent(keyEvent)
    return true
  }

  internal fun createKeyEvent(source: Component, keyEventKind: Int, virtualKey: Int, modifierFlags: Int, keyEventLParam: Int): KeyEvent? {
    val eventId = when (keyEventKind) {
      KEY_EVENT_KIND_KEY_DOWN, KEY_EVENT_KIND_SYSTEM_KEY_DOWN -> KeyEvent.KEY_PRESSED
      KEY_EVENT_KIND_KEY_UP, KEY_EVENT_KIND_SYSTEM_KEY_UP -> KeyEvent.KEY_RELEASED
      else -> return null
    }

    val keyCode = windowsVirtualKeyToJavaKeyCode(virtualKey)
    return KeyEvent(
      source,
      eventId,
      System.currentTimeMillis(),
      modifierFlagsToJavaModifiers(modifierFlags),
      keyCode,
      KeyEvent.CHAR_UNDEFINED,
      keyLocation(virtualKey, keyEventLParam),
    )
  }

  internal fun isShortcutCandidate(keyCode: Int, modifiersEx: Int): Boolean {
    if (keyCode == KeyEvent.VK_UNDEFINED || isModifierKey(keyCode)) return false
    if (isBrowserEditingShortcut(keyCode, modifiersEx)) return false

    val commandModifiers = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
    return modifiersEx and commandModifiers != 0 || keyCode in KeyEvent.VK_F1..KeyEvent.VK_F24 || keyCode == KeyEvent.VK_ESCAPE
  }

  private fun isBrowserEditingShortcut(keyCode: Int, modifiersEx: Int): Boolean {
    val relevantModifiers = modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK)
    return when (keyCode) {
      KeyEvent.VK_A,
      KeyEvent.VK_C,
      KeyEvent.VK_V,
      KeyEvent.VK_X,
      KeyEvent.VK_Y,
      KeyEvent.VK_Z -> relevantModifiers == InputEvent.CTRL_DOWN_MASK
      KeyEvent.VK_INSERT -> relevantModifiers == InputEvent.CTRL_DOWN_MASK || relevantModifiers == InputEvent.SHIFT_DOWN_MASK
      KeyEvent.VK_DELETE -> relevantModifiers == InputEvent.SHIFT_DOWN_MASK
      else -> false
    }
  }

  private fun modifierFlagsToJavaModifiers(modifierFlags: Int): Int {
    var result = 0
    if (modifierFlags and MODIFIER_SHIFT != 0) result = result or InputEvent.SHIFT_DOWN_MASK
    if (modifierFlags and MODIFIER_CONTROL != 0) result = result or InputEvent.CTRL_DOWN_MASK
    if (modifierFlags and MODIFIER_ALT != 0) result = result or InputEvent.ALT_DOWN_MASK
    if (modifierFlags and MODIFIER_META != 0) result = result or InputEvent.META_DOWN_MASK
    return result
  }

  private fun windowsVirtualKeyToJavaKeyCode(virtualKey: Int): Int {
    return WINDOWS_TO_JAVA_KEY_CODES[virtualKey] ?: virtualKey
  }

  private fun keyLocation(virtualKey: Int, keyEventLParam: Int): Int {
    val extended = keyEventLParam and EXTENDED_KEY_MASK != 0
    return when (virtualKey) {
      VK_LSHIFT, VK_LCONTROL, VK_LMENU, VK_LWIN -> KeyEvent.KEY_LOCATION_LEFT
      VK_RSHIFT, VK_RCONTROL, VK_RMENU, VK_RWIN -> KeyEvent.KEY_LOCATION_RIGHT
      VK_SHIFT -> KeyEvent.KEY_LOCATION_UNKNOWN
      VK_CONTROL, VK_MENU -> if (extended) KeyEvent.KEY_LOCATION_RIGHT else KeyEvent.KEY_LOCATION_LEFT
      in VK_NUMPAD0..VK_DIVIDE -> KeyEvent.KEY_LOCATION_NUMPAD
      else -> KeyEvent.KEY_LOCATION_STANDARD
    }
  }

  private fun isModifierKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.VK_SHIFT ||
           keyCode == KeyEvent.VK_CONTROL ||
           keyCode == KeyEvent.VK_ALT ||
           keyCode == KeyEvent.VK_META ||
           keyCode == KeyEvent.VK_ALT_GRAPH
  }

  private val WINDOWS_TO_JAVA_KEY_CODES = mapOf(
    VK_RETURN to KeyEvent.VK_ENTER,
    VK_PRIOR to KeyEvent.VK_PAGE_UP,
    VK_NEXT to KeyEvent.VK_PAGE_DOWN,
    VK_INSERT to KeyEvent.VK_INSERT,
    VK_DELETE to KeyEvent.VK_DELETE,
    VK_SNAPSHOT to KeyEvent.VK_PRINTSCREEN,
    VK_HELP to KeyEvent.VK_HELP,
    VK_LSHIFT to KeyEvent.VK_SHIFT,
    VK_RSHIFT to KeyEvent.VK_SHIFT,
    VK_LCONTROL to KeyEvent.VK_CONTROL,
    VK_RCONTROL to KeyEvent.VK_CONTROL,
    VK_LMENU to KeyEvent.VK_ALT,
    VK_RMENU to KeyEvent.VK_ALT,
    VK_LWIN to KeyEvent.VK_META,
    VK_RWIN to KeyEvent.VK_META,
    VK_APPS to KeyEvent.VK_CONTEXT_MENU,
    VK_OEM_1 to KeyEvent.VK_SEMICOLON,
    VK_OEM_PLUS to KeyEvent.VK_EQUALS,
    VK_OEM_COMMA to KeyEvent.VK_COMMA,
    VK_OEM_MINUS to KeyEvent.VK_MINUS,
    VK_OEM_PERIOD to KeyEvent.VK_PERIOD,
    VK_OEM_2 to KeyEvent.VK_SLASH,
    VK_OEM_3 to KeyEvent.VK_BACK_QUOTE,
    VK_OEM_4 to KeyEvent.VK_OPEN_BRACKET,
    VK_OEM_5 to KeyEvent.VK_BACK_SLASH,
    VK_OEM_6 to KeyEvent.VK_CLOSE_BRACKET,
    VK_OEM_7 to KeyEvent.VK_QUOTE,
    VK_OEM_102 to KeyEvent.VK_LESS,
  )

  private const val EXTENDED_KEY_MASK: Int = 1 shl 24
  private const val VK_RETURN: Int = 0x0D
  private const val VK_PRIOR: Int = 0x21
  private const val VK_NEXT: Int = 0x22
  private const val VK_INSERT: Int = 0x2D
  private const val VK_DELETE: Int = 0x2E
  private const val VK_SNAPSHOT: Int = 0x2C
  private const val VK_HELP: Int = 0x2F
  private const val VK_SHIFT: Int = 0x10
  private const val VK_CONTROL: Int = 0x11
  private const val VK_MENU: Int = 0x12
  private const val VK_LWIN: Int = 0x5B
  private const val VK_RWIN: Int = 0x5C
  private const val VK_APPS: Int = 0x5D
  private const val VK_NUMPAD0: Int = 0x60
  private const val VK_DIVIDE: Int = 0x6F
  private const val VK_LSHIFT: Int = 0xA0
  private const val VK_RSHIFT: Int = 0xA1
  private const val VK_LCONTROL: Int = 0xA2
  private const val VK_RCONTROL: Int = 0xA3
  private const val VK_LMENU: Int = 0xA4
  private const val VK_RMENU: Int = 0xA5
  private const val VK_OEM_1: Int = 0xBA
  private const val VK_OEM_PLUS: Int = 0xBB
  private const val VK_OEM_COMMA: Int = 0xBC
  private const val VK_OEM_MINUS: Int = 0xBD
  private const val VK_OEM_PERIOD: Int = 0xBE
  private const val VK_OEM_2: Int = 0xBF
  private const val VK_OEM_3: Int = 0xC0
  private const val VK_OEM_4: Int = 0xDB
  private const val VK_OEM_5: Int = 0xDC
  private const val VK_OEM_6: Int = 0xDD
  private const val VK_OEM_7: Int = 0xDE
  private const val VK_OEM_102: Int = 0xE2
}
