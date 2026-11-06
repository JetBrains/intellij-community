// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class TerminalMultilineCommandExecutionTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {
  @Test
  fun `multiline command uses the current mode without waiting for shell integration`() = doTest { fixture ->
    val command = "echo first\necho second"
    assertThat(fixture.view.bracketedPasteMode.value).isFalse()
    assertThat(fixture.view.shellIntegrationDeferred.isCompleted).isFalse()
    assertThat(fixture.awaitInputEventsHandled(timeout = 2.seconds) {
      fixture.view.createSendTextBuilder().useBracketedPasteMode().shouldExecute().send(command)
    }).containsExactly("echo first\recho second\r")

    fixture.connector.feed("\u001b[?2004h")
    withTimeout(2.seconds) { fixture.view.bracketedPasteMode.first { it } }
    assertThat(fixture.awaitInputEventsHandled(timeout = 2.seconds) {
      fixture.view.createSendTextBuilder().useBracketedPasteMode().shouldExecute().send(command)
    }).containsExactly("\u001b[200~echo first\recho second\u001b[201~\r")

    fixture.connector.feed("\u001b[?2004l")
    withTimeout(2.seconds) { fixture.view.bracketedPasteMode.first { !it } }
  }
}
