// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end coverage of OSC8 hyperlinks: a real [TerminalViewImpl] is connected to the
 * production [org.jetbrains.plugins.terminal.session.impl.TerminalSession], backed by a [LoopbackTtyConnector]
 * instead of a real shell process. Raw `OSC 8` escape sequences are fed through the connector, and the final
 * state is asserted where the UI actually renders it: a hyperlink [RangeHighlighter] in the output editor's
 * markup model.
 */
@RunWith(JUnit4::class)
internal class TerminalOsc8HyperlinksEndToEndTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `OSC8 hyperlink is rendered as a hyperlink highlighter in the editor markup model`(): Unit = doTest { fixture ->
    fixture.connector.feed("before ${osc8("https://example.com", "link text")} after")

    val highlighter = fixture.awaitHyperlinkHighlighter()
    assertThat(fixture.textOf(highlighter)).isEqualTo("link text")
  }

  private fun doTest(test: suspend (Fixture) -> Unit) {
    return timeoutRunBlocking(20.seconds, context = Dispatchers.EDT) {
      Fixture(project).use { fixture -> test(fixture) }
    }
  }

  private fun osc8(uri: String, text: String): String = "$OSC8_PREFIX$uri$ST$text$OSC8_PREFIX$ST"

  /**
   * A real [TerminalViewImpl] connected to the production `TerminalSession`, backed by a [LoopbackTtyConnector],
   * so the whole OSC8 pipeline - emulation, scraping, output model application and markup rendering
   * (registered on [TerminalViewImpl.outputEditor]) - runs exactly as in production.
   */
  private class Fixture(project: Project) : AutoCloseable {
    private val scope = terminalProjectScope(project).childScope("TerminalViewImpl")

    val connector: LoopbackTtyConnector
    val editor: EditorImpl

    init {
      val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(project, scope)
      this.connector = connector

      val terminalView = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, scope)
      terminalView.connectToSession(session)
      editor = terminalView.outputEditor as EditorImpl
    }

    fun textOf(highlighter: RangeHighlighter): String {
      return editor.document.getText(highlighter.textRange)
    }

    /**
     * Polls the output editor's markup model until exactly one hyperlink highlighter is present.
     * Reconciliation between the output model and the markup model runs on its own delay
     * (see `installOsc8HyperlinksProcessing`), not synchronously with every output model change.
     */
    suspend fun awaitHyperlinkHighlighter(): RangeHighlighter {
      while (true) {
        val highlighters = withContext(Dispatchers.EDT) {
          editor.markupModel.allHighlighters.filter { it.isValid && it.layer == HighlighterLayer.HYPERLINK }
        }
        if (highlighters.size == 1) return highlighters.single()
        delay(50.milliseconds)
      }
    }

    override fun close() {
      scope.cancel()
    }
  }

  companion object {
    private val ESC: String = Char(0x1B).toString()

    /** OSC 8 introducer with empty params: `ESC ] 8 ; ;`. */
    private val OSC8_PREFIX: String = "$ESC]8;;"

    /** String Terminator: `ESC \`. */
    private val ST: String = "$ESC\\"
  }
}
