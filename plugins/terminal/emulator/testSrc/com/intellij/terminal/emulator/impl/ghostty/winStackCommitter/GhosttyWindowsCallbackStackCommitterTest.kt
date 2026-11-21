// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.winStackCommitter

import com.intellij.terminal.emulator.TerminalGridGeometry
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.createTerminalEmulator
import com.intellij.terminal.emulator.impl.ghostty.GhosttyWindowsCallbackStackCommitter
import com.intellij.terminal.emulator.impl.ghostty.winStackCommitter.WindowsThreadStack.DEEP_CALL_DEPTH
import com.intellij.terminal.emulator.impl.ghostty.winStackCommitter.WindowsThreadStack.MAX_IDLE_GROWTH
import com.intellij.terminal.emulator.impl.ghostty.winStackCommitter.WindowsThreadStack.MIN_COMMIT_GROWTH
import com.intellij.terminal.emulator.impl.ghostty.winStackCommitter.WindowsThreadStack.atDepth
import com.intellij.terminal.emulator.impl.ghostty.winStackCommitter.WindowsThreadStack.committedGrowth
import com.intellij.terminal.emulator.impl.ghostty.winStackCommitter.WindowsThreadStack.onNewThread
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Checks where [GhosttyWindowsCallbackStackCommitter] commits the stack. Each check runs on a new thread
 * and measures how much the committed part of the stack grows, with [WindowsThreadStack].
 *
 * [EmulatorThreadStackTest] needs the JVM to reach the crash conditions, and it covers only `ghostty_terminal_vt_write`.
 * These tests need no crash, and they also cover `ghostty_terminal_resize`.
 * Without the `commit` call in the `write` or `resize` function of the emulator, the measured growth is 0.
 *
 * These tests have no application, so the `terminal.ghostty.commit.stack.on.each.call` registry key is off.
 * No test covers the key.
 */
@EnabledOnOs(OS.WINDOWS)
class GhosttyWindowsCallbackStackCommitterTest {
  @Test
  fun `the first commit on a thread commits the stack`() {
    val committer = GhosttyWindowsCallbackStackCommitter()
    val growth = onNewThread { committedGrowth { committer.commit() } }
    assertThat(growth).isGreaterThanOrEqualTo(MIN_COMMIT_GROWTH)
  }

  @Test
  fun `a later commit on the same thread does nothing`() {
    val committer = GhosttyWindowsCallbackStackCommitter()
    val growth = onNewThread {
      committer.commit()
      atDepth(DEEP_CALL_DEPTH) { committedGrowth { committer.commit() } }
    }
    assertThat(growth).isLessThan(MAX_IDLE_GROWTH)
  }

  @Test
  fun `a different instance commits the stack on the same thread`() {
    val first = GhosttyWindowsCallbackStackCommitter()
    val second = GhosttyWindowsCallbackStackCommitter()
    val growth = onNewThread {
      first.commit()
      atDepth(DEEP_CALL_DEPTH) { committedGrowth { second.commit() } }
    }
    assertThat(growth).isGreaterThanOrEqualTo(MIN_COMMIT_GROWTH)
  }

  @Test
  fun `the first write of an emulator on a thread commits the stack`() {
    createTerminalEmulator(TerminalGridGeometry(TerminalSize(80, 24))).use { emulator ->
      // The first call of a native function runs deep JVM code. It must not be a part of the measurement.
      emulator.write(TEXT)
      val growth = onNewThread { committedGrowth { emulator.write(TEXT) } }
      assertThat(growth).isGreaterThanOrEqualTo(MIN_COMMIT_GROWTH)
    }
  }

  @Test
  fun `the first resize of an emulator on a thread commits the stack`() {
    createTerminalEmulator(TerminalGridGeometry(TerminalSize(80, 24))).use { emulator ->
      // The first call of a native function runs deep JVM code. It must not be a part of the measurement.
      emulator.resize(TerminalGridGeometry(TerminalSize(90, 30)))
      val growth = onNewThread { committedGrowth { emulator.resize(TerminalGridGeometry(TerminalSize(100, 40))) } }
      assertThat(growth).isGreaterThanOrEqualTo(MIN_COMMIT_GROWTH)
    }
  }

  private companion object {
    private val TEXT: ByteArray = "text".encodeToByteArray()
  }
}
