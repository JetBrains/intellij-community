// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.BlockTerminalColors
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.awt.Color
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The Ghostty-backed [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession] pushes the terminal colors
 * of the global color scheme ([BlockTerminalColors.DEFAULT_FOREGROUND] and [BlockTerminalColors.DEFAULT_BACKGROUND])
 * into the emulator as its default colors, and keeps them in sync with the global color scheme.
 * A program reads them with the color queries `OSC 10 ; ?` and `OSC 11 ; ?`.
 */
internal class GhosttyTerminalSessionColorSchemeTest : GhosttyTerminalSessionTestCase() {
  private var testSchemeCount = 0

  @Test
  fun `color queries report the terminal colors of the global color scheme`() {
    setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0x01, 0x02, 0x03))

    runSessionTest { _, connector, _ ->
      assertThat(connector.queryColor(osc("10;?"))).isEqualTo(osc("10;rgb:1010/0f0f/0e0e"))
      assertThat(connector.queryColor(osc("11;?"))).isEqualTo(osc("11;rgb:0101/0202/0303"))
    }
  }

  @Test
  fun `a global color scheme change mid-session updates the reported colors`() {
    setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0x01, 0x02, 0x03))

    runSessionTest { _, connector, _ ->
      assertThat(connector.queryColor(osc("11;?"))).isEqualTo(osc("11;rgb:0101/0202/0303"))

      setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x20, 0x30, 0x40))

      assertThat(connector.queryColor(osc("10;?"))).isEqualTo(osc("10;rgb:f0f0/e0e0/d0d0"))
      assertThat(connector.queryColor(osc("11;?"))).isEqualTo(osc("11;rgb:2020/3030/4040"))
    }
  }

  @Test
  fun `a program color override survives a global color scheme change`() {
    setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0x01, 0x02, 0x03))

    runSessionTest { _, connector, _ ->
      connector.feed(osc("11;#aabbcc"))
      setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x20, 0x30, 0x40))

      assertThat(connector.queryColor(osc("11;?")))
        .describedAs("a color scheme change must not replace an active program override")
        .isEqualTo(osc("11;rgb:aaaa/bbbb/cccc"))

      // OSC 111 resets the override: the reply falls back to the new color scheme.
      assertThat(connector.queryColor(osc("111") + osc("11;?"))).isEqualTo(osc("11;rgb:2020/3030/4040"))
    }
  }

  /** Feeds [query] to the session and returns the first reply that the session writes to the pty. */
  private fun LoopbackTtyConnector.queryColor(query: String): String? {
    val replies = LinkedBlockingQueue<String>()
    responseHandler = { bytes -> replies.add(String(bytes, Charsets.UTF_8)) }
    feed(query)
    return replies.poll(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  /**
   * Makes a copy of the global color scheme with the given terminal colors the global scheme, until the end of the test.
   * The scheme switch is synchronous, so the session gets [EditorColorsManager.TOPIC] before this function returns.
   */
  private fun setGlobalSchemeColorsForTest(foreground: Color, background: Color) {
    val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    val originalScheme = manager.globalScheme
    val scheme = (originalScheme.clone() as EditorColorsScheme).apply {
      name = "GhosttyTerminalSessionColorSchemeTest #${++testSchemeCount}"
      setColor(BlockTerminalColors.DEFAULT_FOREGROUND, foreground)
      setColor(BlockTerminalColors.DEFAULT_BACKGROUND, background)
    }
    manager.addColorScheme(scheme)
    runInEdtAndWait { manager.setGlobalScheme(scheme, processChangeSynchronously = true) }
    Disposer.register(testDisposable) {
      runInEdtAndWait { manager.setGlobalScheme(originalScheme, processChangeSynchronously = true) }
      manager.schemeManager.removeScheme(scheme)
    }
  }
}
