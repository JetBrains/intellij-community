// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.openapi.application.UI
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.redispatchTerminalKeyEvent
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.event.KeyEvent
import java.io.ByteArrayOutputStream

@TestApplication
internal class KetraTermKeyRoutingTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `native control keys reach the session after shortcut processing`(consumed: Boolean): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm key routing", Dispatchers.Default)
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val written = ByteArrayOutputStream()
    val received = CompletableDeferred<Unit>()
    val expected = "\r\t\u001b[C"
    connector.responseHandler = { bytes ->
      synchronized(written) {
        written.write(bytes)
        if (written.size() >= expected.length) received.complete(Unit)
      }
    }
    try {
      withContext(Dispatchers.UI) {
        val view = KetraTermTerminalView(projectFixture.get(), JBTerminalSystemSettingsProvider(), scope)
        view.terminal.size = view.terminal.preferredGridSize(80, 24)
        view.connectToSession(session)
        for (code in intArrayOf(KeyEvent.VK_ENTER, KeyEvent.VK_TAB, KeyEvent.VK_RIGHT)) {
          for (id in intArrayOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
            val event = KeyEvent(view.terminal, id, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED)
            if (consumed) event.consume()
            redispatchTerminalKeyEvent(view.terminal, event)
          }
        }
      }
      received.await()
      assertThat(synchronized(written) { written.toString(Charsets.UTF_8) }).isEqualTo(expected)
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
