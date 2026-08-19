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
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.MouseEvent
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
  fun `OSC8 hyperlink text can differ from its target URI`(): Unit = doTest { fixture ->
    fixture.connector.feed("before ${osc8("https://example.com", "link text")} after")

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("link text")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://example.com")
  }

  @Test
  fun `several OSC8 hyperlinks in the same output are each rendered separately`(): Unit = doTest { fixture ->
    fixture.connector.feed("${osc8("https://jetbrains.com", "FIRST")} middle ${osc8("https://example.com", "SECOND")}")

    val highlighters = fixture.awaitHyperlinks(2)
    assertThat(highlighters.map { fixture.textOf(it) }).containsExactly("FIRST", "SECOND")
    assertThat(highlighters.map { fixture.uriOf(it) }).containsExactly("https://jetbrains.com", "https://example.com")
  }

  @Test
  fun `two adjacent OSC8 writes with the same URI are rendered as a single hyperlink`(): Unit = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "foo") + osc8("https://jetbrains.com", "bar"))

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("foobar")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://jetbrains.com")
  }

  @Test
  fun `hyperlink split into several ranges by an in-place edit is still rendered as a single hyperlink`(): Unit = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "world"))
    fixture.connector.feed("${ESC}[3D") // move the cursor back to the "r" in "world"
    fixture.connector.feed(osc8("https://jetbrains.com", "R"))

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("woRld")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://jetbrains.com")
  }

  @Test
  fun `adjacent OSC8 hyperlinks with different URIs are not collapsed into one`(): Unit = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "foo") + osc8("https://example.com", "bar"))

    val highlighters = fixture.awaitHyperlinks(2)
    assertThat(highlighters.map { fixture.textOf(it) }).containsExactly("foo", "bar")
    assertThat(highlighters.map { fixture.uriOf(it) }).containsExactly("https://jetbrains.com", "https://example.com")
  }

  @Test
  fun `a target that is not a recognized URL is not rendered as a hyperlink`(): Unit = doTest { fixture ->
    fixture.connector.feed(osc8("definitely-not-a-url", "click me"))
    // A real link fed right after: once it's rendered, reconciliation has scanned the whole link list
    // (including the one above) at least once, so a missing highlighter for "click me" isn't just a timing fluke.
    fixture.connector.feed(osc8("https://jetbrains.com", "SENTINEL"))

    val highlighters = fixture.awaitHyperlinks(1)
    assertThat(fixture.textOf(highlighters.single())).isEqualTo("SENTINEL")
  }

  @Test
  fun `hyperlink is removed from the markup model once its line is overwritten with plain text`(): Unit = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "LINK"))
    fixture.awaitHyperlink()

    // Move the cursor back to the start of the line and overwrite it with plain text at least as long as "LINK".
    fixture.connector.feed("\rplain text, no links here")

    fixture.awaitHyperlinks(0)
  }

  @Test
  fun `hovering an OSC8 hyperlink shows its target URI as a tooltip`(): Unit = doTest { fixture ->
    fixture.connector.feed("x ${osc8("https://jetbrains.com", "JB")} y")

    val highlighter = fixture.awaitHyperlink()
    fixture.hover(highlighter)

    // The link text ("JB") says nothing about the destination - the tooltip is what reveals it.
    assertThat(fixture.editor.contentComponent.toolTipText).contains("https://jetbrains.com")
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
      // So offsetToXY (used by hover()) produces valid screen coordinates in a headless test.
      editor.component.setSize(800, 600)
    }

    fun textOf(highlighter: RangeHighlighter): String {
      return editor.document.getText(highlighter.textRange)
    }

    /**
     * The target URI of the OSC8 link rendered as [highlighter], read from the output model - the
     * markup model's own decoration doesn't expose it (it's only used internally to build the click action).
     */
    fun uriOf(highlighter: RangeHighlighter): String {
      val model = editor.getUserData(TerminalOutputModel.KEY)!!
      return model.getOsc8Hyperlinks().single {
        (it.startOffset - model.startOffset).toInt() == highlighter.startOffset &&
        (it.endOffset - model.startOffset).toInt() == highlighter.endOffset
      }.uri
    }

    /** Moves the mouse over the middle of [highlighter]'s range, as a real mouse move would. */
    fun hover(highlighter: RangeHighlighter) {
      val offset = (highlighter.startOffset + highlighter.endOffset) / 2
      val point = editor.offsetToXY(offset)
      val event = MouseEvent(
        editor.contentComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, point.x, point.y, 1, false, MouseEvent.BUTTON1
      )
      editor.contentComponent.mouseMotionListeners.forEach { it.mouseMoved(event) }
    }

    suspend fun awaitHyperlink(): RangeHighlighter = awaitHyperlinks(1).single()

    /**
     * Polls the output editor's markup model until exactly [count] hyperlink highlighters are present,
     * then returns them sorted by position.
     */
    suspend fun awaitHyperlinks(count: Int): List<RangeHighlighter> {
      while (true) {
        val highlighters = withContext(Dispatchers.EDT) {
          editor.markupModel.allHighlighters.filter { it.isValid && it.layer == HighlighterLayer.HYPERLINK }
        }
        if (highlighters.size == count) return highlighters.sortedBy { it.startOffset }
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
