// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// Key events the embedder asks the emulator to encode into PTY bytes: TerminalKeyEvent and its
// enums. Part of the backend-agnostic API; see TerminalEmulator.kt.

/**
 * Physical key codes, layout-independent, following the W3C UI Events `code` values
 * (https://www.w3.org/TR/uievents-code). The layout-dependent text a key produces travels
 * separately in [TerminalKeyEvent.text].
 *
 * The declaration order mirrors `GhosttyKey` (`ghostty/vt/key/event.h`), and the Ghostty backend
 * uses the ordinal as the C ABI value: do not reorder or insert entries. The encoding tests fail
 * loudly if the orders drift apart.
 */
@Suppress("unused") // the complete physical-key set is the API, referenced or not
@ApiStatus.Internal
enum class TerminalKey {
  UNIDENTIFIED,

  // Writing system keys
  BACKQUOTE,
  BACKSLASH,
  BRACKET_LEFT,
  BRACKET_RIGHT,
  COMMA,
  DIGIT_0,
  DIGIT_1,
  DIGIT_2,
  DIGIT_3,
  DIGIT_4,
  DIGIT_5,
  DIGIT_6,
  DIGIT_7,
  DIGIT_8,
  DIGIT_9,
  EQUAL,
  INTL_BACKSLASH,
  INTL_RO,
  INTL_YEN,
  A,
  B,
  C,
  D,
  E,
  F,
  G,
  H,
  I,
  J,
  K,
  L,
  M,
  N,
  O,
  P,
  Q,
  R,
  S,
  T,
  U,
  V,
  W,
  X,
  Y,
  Z,
  MINUS,
  PERIOD,
  QUOTE,
  SEMICOLON,
  SLASH,

  // Functional keys
  ALT_LEFT,
  ALT_RIGHT,
  BACKSPACE,
  CAPS_LOCK,
  CONTEXT_MENU,
  CONTROL_LEFT,
  CONTROL_RIGHT,
  ENTER,
  META_LEFT,
  META_RIGHT,
  SHIFT_LEFT,
  SHIFT_RIGHT,
  SPACE,
  TAB,
  CONVERT,
  KANA_MODE,
  NON_CONVERT,

  // Control pad
  DELETE,
  END,
  HELP,
  HOME,
  INSERT,
  PAGE_DOWN,
  PAGE_UP,

  // Arrow pad
  ARROW_DOWN,
  ARROW_LEFT,
  ARROW_RIGHT,
  ARROW_UP,

  // Numpad
  NUM_LOCK,
  NUMPAD_0,
  NUMPAD_1,
  NUMPAD_2,
  NUMPAD_3,
  NUMPAD_4,
  NUMPAD_5,
  NUMPAD_6,
  NUMPAD_7,
  NUMPAD_8,
  NUMPAD_9,
  NUMPAD_ADD,
  NUMPAD_BACKSPACE,
  NUMPAD_CLEAR,
  NUMPAD_CLEAR_ENTRY,
  NUMPAD_COMMA,
  NUMPAD_DECIMAL,
  NUMPAD_DIVIDE,
  NUMPAD_ENTER,
  NUMPAD_EQUAL,
  NUMPAD_MEMORY_ADD,
  NUMPAD_MEMORY_CLEAR,
  NUMPAD_MEMORY_RECALL,
  NUMPAD_MEMORY_STORE,
  NUMPAD_MEMORY_SUBTRACT,
  NUMPAD_MULTIPLY,
  NUMPAD_PAREN_LEFT,
  NUMPAD_PAREN_RIGHT,
  NUMPAD_SUBTRACT,
  NUMPAD_SEPARATOR,
  NUMPAD_UP,
  NUMPAD_DOWN,
  NUMPAD_RIGHT,
  NUMPAD_LEFT,
  NUMPAD_BEGIN,
  NUMPAD_HOME,
  NUMPAD_END,
  NUMPAD_INSERT,
  NUMPAD_DELETE,
  NUMPAD_PAGE_UP,
  NUMPAD_PAGE_DOWN,

  // Function section
  ESCAPE,
  F1,
  F2,
  F3,
  F4,
  F5,
  F6,
  F7,
  F8,
  F9,
  F10,
  F11,
  F12,
  F13,
  F14,
  F15,
  F16,
  F17,
  F18,
  F19,
  F20,
  F21,
  F22,
  F23,
  F24,
  F25,
  FN,
  FN_LOCK,
  PRINT_SCREEN,
  SCROLL_LOCK,
  PAUSE,

  // Media keys
  BROWSER_BACK,
  BROWSER_FAVORITES,
  BROWSER_FORWARD,
  BROWSER_HOME,
  BROWSER_REFRESH,
  BROWSER_SEARCH,
  BROWSER_STOP,
  EJECT,
  LAUNCH_APP_1,
  LAUNCH_APP_2,
  LAUNCH_MAIL,
  MEDIA_PLAY_PAUSE,
  MEDIA_SELECT,
  MEDIA_STOP,
  MEDIA_TRACK_NEXT,
  MEDIA_TRACK_PREVIOUS,
  POWER,
  SLEEP,
  AUDIO_VOLUME_DOWN,
  AUDIO_VOLUME_MUTE,
  AUDIO_VOLUME_UP,
  WAKE_UP,

  // Legacy and special keys
  COPY,
  CUT,
  PASTE,
}

/** What happened to the key. Terminals only see releases when the Kitty keyboard protocol asks for them. */
@ApiStatus.Internal
enum class TerminalKeyAction {
  PRESS,
  RELEASE,
  REPEAT,
}

/**
 * A key event to encode into PTY bytes via [TerminalEmulator.encodeKeyEvent].
 *
 * @param key the physical key, layout-independent.
 * @param action press/repeat/release; releases produce bytes only under the Kitty keyboard protocol.
 * @param modifiers modifier state; [TerminalInputModifier.CAPS_LOCK] and [TerminalInputModifier.NUM_LOCK]
 *   are lock states, not held keys.
 * @param text the text this key produced in the current keyboard layout ("a", "A", "ф", …); empty when
 *   the key produces none (arrows, F-keys) or the platform suppressed it (e.g. a Ctrl chord).
 * @param unshiftedCodepoint the code point the key produces in the current layout without modifiers
 *   (`'a'.code` for the A key); 0 when not applicable. Used to derive control characters and Kitty
 *   key codes for keys whose [text] is suppressed by modifiers.
 * @param composing whether an IME composition is in progress; composing events produce no bytes.
 */
@ApiStatus.Internal
class TerminalKeyEvent(
  val key: TerminalKey,
  val action: TerminalKeyAction = TerminalKeyAction.PRESS,
  val modifiers: Set<TerminalInputModifier> = emptySet(),
  val text: String = "",
  val unshiftedCodepoint: Int = 0,
  val composing: Boolean = false,
)
