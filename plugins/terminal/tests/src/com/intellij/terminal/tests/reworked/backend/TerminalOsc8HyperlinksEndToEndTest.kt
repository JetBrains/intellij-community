// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.awaitEvent
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputOsc8Hyperlink
import org.jetbrains.plugins.terminal.view.impl.updateContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end coverage of OSC8 hyperlinks for the JediTerm engine: raw `OSC 8` escape sequences are written to a
 * [LoopbackTtyConnector] and go through the production `createTerminalSession` (real emulation +
 * `JediTermOsc8HyperlinkFilter`), and the resulting [TerminalContentUpdatedEvent]s are asserted both directly and
 * after being applied to a [org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel].
 *
 * This exercises the whole producer chain that the more targeted unit tests intentionally bypass:
 * emulator OSC8 parsing -> hyperlink filter -> scraper span extraction -> event serialization -> model application.
 */
@RunWith(JUnit4::class)
internal class TerminalOsc8HyperlinksEndToEndTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  @Test
  fun `OSC8 hyperlink is reported on the content update event`() = runSessionTest { session, connector ->
    val collector = TerminalOutputEventCollector(session, this)
    connector.feed("x ${osc8("https://jetbrains.com", "JB")} y")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.osc8Hyperlinks.isNotEmpty() }
    val link = event.osc8Hyperlinks.single()
    assertThat(link.uri).isEqualTo("https://jetbrains.com")
    assertThat(event.text.substring(link.startOffset.toInt(), link.endOffset.toInt())).isEqualTo("JB")
  }

  @Test
  fun `OSC8 hyperlink is stored in the output model after applying the events`() = runSessionTest { session, connector ->
    val model = TerminalTestUtil.createOutputModel()
    launch(Dispatchers.EDT) {
      session.getOutputFlow().collect { events ->
        events.filterIsInstance<TerminalContentUpdatedEvent>().forEach { model.updateContent(it) }
      }
    }

    connector.feed("before ${osc8("https://example.com", "link text")} after")

    val link = awaitOsc8Hyperlink(model)
    assertThat(link.uri).isEqualTo("https://example.com")
    assertThat(model.getText(link.startOffset, link.endOffset).toString()).isEqualTo("link text")
  }

  private fun runSessionTest(
    test: suspend CoroutineScope.(session: TerminalSession, connector: LoopbackTtyConnector) -> Unit,
  ) {
    timeoutRunBlocking(20.seconds) {
      val sessionScope = childScope("TerminalSession")
      try {
        val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(projectRule.project, sessionScope)
        sessionScope.test(session, connector)
      }
      finally {
        sessionScope.cancel()
      }
    }
  }

  private suspend fun awaitOsc8Hyperlink(model: TerminalOutputModel): TerminalOutputOsc8Hyperlink {
    while (true) {
      val link = withContext(Dispatchers.EDT) { model.getOsc8Hyperlinks().firstOrNull() }
      if (link != null) return link
      delay(50.milliseconds)
    }
  }

  private fun osc8(uri: String, text: String): String = "$OSC8_PREFIX$uri$ST$text$OSC8_PREFIX$ST"

  companion object {
    private val ESC: String = Char(0x1B).toString()

    /** OSC 8 introducer with empty params: `ESC ] 8 ; ;`. */
    private val OSC8_PREFIX: String = "$ESC]8;;"

    /** String Terminator: `ESC \`. */
    private val ST: String = "$ESC\\"
  }
}
