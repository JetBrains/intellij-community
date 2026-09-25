// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.BlockTerminalColors
import com.intellij.terminal.tests.reworked.util.BEL
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * [com.intellij.terminal.frontend.view.impl.TerminalViewImpl] sends the terminal colors of the global color scheme
 * ([BlockTerminalColors.DEFAULT_FOREGROUND] and [BlockTerminalColors.DEFAULT_BACKGROUND]) to the session as its default colors,
 * and sends them again when the global color scheme changes.
 * A program reads them with the color queries `OSC 10 ; ?` and `OSC 11 ; ?`, and asks if they are dark with `CSI ? 996 n`.
 * While a program enables the color scheme reports (mode 2031), each change sends a report `CSI ? 997 ; Ps n`.
 *
 * Ghostty-only: JediTerm reads the default colors on demand and ignores a program override.
 */
internal class TerminalSessionColorSchemeUpdatingTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {
  private var testSchemeCount = 0

  @BeforeEach
  fun assumeGhosttyEmulator() {
    assumeGhostty()
  }

  @Test
  fun `color queries report the terminal colors of the global color scheme`() {
    setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0x01, 0x02, 0x03))

    doTest { fixture ->
      assertThat(fixture.queryColor(osc("10;?"))).isEqualTo(osc("10;rgb:1010/0f0f/0e0e"))
      assertThat(fixture.queryColor(osc("11;?"))).isEqualTo(osc("11;rgb:0101/0202/0303"))
    }
  }

  @Test
  fun `a global color scheme change mid-session updates the reported colors`() {
    setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0x01, 0x02, 0x03))

    doTest { fixture ->
      assertThat(fixture.queryColor(osc("11;?"))).isEqualTo(osc("11;rgb:0101/0202/0303"))

      setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x20, 0x30, 0x40))

      assertThat(fixture.queryColor(osc("10;?"))).isEqualTo(osc("10;rgb:f0f0/e0e0/d0d0"))
      assertThat(fixture.queryColor(osc("11;?"))).isEqualTo(osc("11;rgb:2020/3030/4040"))
    }
  }

  @Test
  fun `a program color override survives a global color scheme change`() {
    setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0x01, 0x02, 0x03))

    doTest { fixture ->
      // The reply proves that the session applied the override before the color scheme change below.
      assertThat(fixture.queryColor(osc("11;#aabbcc") + osc("11;?"))).isEqualTo(osc("11;rgb:aaaa/bbbb/cccc"))

      setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x20, 0x30, 0x40))

      assertThat(fixture.queryColor(osc("11;?")))
        .describedAs("a color scheme change must not replace an active program override")
        .isEqualTo(osc("11;rgb:aaaa/bbbb/cccc"))

      // OSC 111 resets the override: the reply falls back to the new color scheme.
      assertThat(fixture.queryColor(osc("111") + osc("11;?"))).isEqualTo(osc("11;rgb:2020/3030/4040"))
    }
  }

  @Test
  fun `the color scheme query reports if the terminal background is dark`() {
    setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x01, 0x02, 0x03))

    doTest { fixture ->
      assertThat(fixture.queryColor(csi("?996n"))).describedAs("a dark background").isEqualTo(csi("?997;1n"))

      setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0xF0, 0xF1, 0xF2))

      assertThat(fixture.queryColor(csi("?996n"))).describedAs("a light background").isEqualTo(csi("?997;2n"))
    }
  }

  @Test
  fun `a global color scheme change sends the color scheme report while a program enables it`() {
    setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x01, 0x02, 0x03))

    doTest { fixture ->
      // The reply proves that the session enabled the mode before the color scheme changes below.
      assertThat(fixture.queryColor(csi("?2031h") + csi("?996n"))).isEqualTo(csi("?997;1n"))

      assertThat(fixture.awaitInputEventsHandled {
        setGlobalSchemeColorsForTest(foreground = Color(0x10, 0x0F, 0x0E), background = Color(0xF0, 0xF1, 0xF2))
      })
        .describedAs("a change to a light background")
        .containsExactly(csi("?997;2n"))

      assertThat(fixture.awaitInputEventsHandled {
        setGlobalSchemeColorsForTest(foreground = Color(0x20, 0x1F, 0x1E), background = Color(0xF0, 0xF1, 0xF2))
      })
        .describedAs("a change of the foreground only: the program must query the new colors")
        .containsExactly(csi("?997;2n"))

      assertThat(fixture.queryColor(csi("?2031l") + csi("?996n"))).isEqualTo(csi("?997;2n"))

      assertThat(fixture.awaitInputEventsHandled {
        setGlobalSchemeColorsForTest(foreground = Color(0xF0, 0xE0, 0xD0), background = Color(0x01, 0x02, 0x03))
      })
        .describedAs("a change after the program disabled the mode")
        .isEmpty()
    }
  }

  /**
   * Feeds [query] to the session and returns the first reply that the session writes to the pty.
   * The session applies the color events asynchronously, so this function first waits until it handles them.
   */
  private suspend fun TerminalViewFixture.queryColor(query: String): String? {
    awaitInputEventsHandled()
    val replies = LinkedBlockingQueue<String>()
    connector.responseHandler = { bytes -> replies.add(String(bytes, Charsets.UTF_8)) }
    connector.feed(query)
    // The poll blocks, so it must not run on the EDT.
    return withContext(Dispatchers.IO) { replies.poll(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
  }

  /**
   * Makes a copy of the global color scheme with the given terminal colors the global scheme, until the end of the test.
   * The scheme switch is synchronous, so the view gets [EditorColorsManager.TOPIC] before this function returns.
   */
  private fun setGlobalSchemeColorsForTest(foreground: Color, background: Color) {
    val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    val originalScheme = manager.globalScheme
    val scheme = (originalScheme.clone() as EditorColorsScheme).apply {
      name = "TerminalColorSchemeUpdatingTest #${++testSchemeCount}"
      setColor(BlockTerminalColors.DEFAULT_FOREGROUND, foreground)
      setColor(BlockTerminalColors.DEFAULT_BACKGROUND, background)
    }
    manager.addColorScheme(scheme)
    runInEdtAndWait { manager.setGlobalScheme(scheme, processChangeSynchronously = true) }
    Disposer.register(disposable) {
      runInEdtAndWait { manager.setGlobalScheme(originalScheme, processChangeSynchronously = true) }
      manager.schemeManager.removeScheme(scheme)
    }
  }

  private fun osc(body: String): String = "$ESC]$body$BEL"

  private fun csi(body: String): String = "$ESC[$body"

  companion object {
    private const val AWAIT_TIMEOUT_MS: Long = 5_000
  }
}
