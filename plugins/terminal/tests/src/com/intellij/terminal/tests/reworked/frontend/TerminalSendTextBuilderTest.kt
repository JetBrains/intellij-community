// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextBuilderImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalSendTextBuilderTest {
  @Test
  fun `required bracketed paste and end-before-text flags are passed to sender`() {
    val sentOptions = ArrayList<TerminalSendTextOptions>()
    val builder = TerminalSendTextBuilderImpl { options ->
      sentOptions += options
      false
    }

    val sent = builder
      .sendEndKeyBeforeText()
      .requireBracketedPasteMode()
      .shouldExecute()
      .trySend("hello\ncontext")

    assertThat(sent).isFalse()
    assertThat(sentOptions).containsExactly(
      TerminalSendTextOptions(
        text = "hello\ncontext",
        shouldExecute = true,
        useBracketedPasteMode = true,
        requireBracketedPasteMode = true,
        sendEndKeyBeforeText = true,
      )
    )
  }
}
