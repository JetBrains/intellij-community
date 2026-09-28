// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalSearchController
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import com.intellij.terminal.tests.reworked.util.assertOutputModelState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.jupiter.api.Test

/**
 * Tests how [TerminalViewImpl] switches between the output editor and the alternate buffer editor
 * when a full-screen program enters and leaves the alternate screen.
 *
 * Every case runs on both VT emulators, see [TerminalViewTestCase].
 */
internal class TerminalAlternateBufferSwitchTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {

  @Test
  fun `the alternate buffer editor is not created before the first switch to the alternate screen`() = doTest { fixture ->
    val view = fixture.view
    fixture.connector.feed("hello")
    fixture.assertOutputModelState(view.outputModels.regular) { it.text.contains("hello") }

    assertThat(view.alternateBufferEditorDeferred.isCompleted).isFalse()

    fixture.enterAlternateScreen()

    assertThat(view.alternateBufferEditorDeferred.isCompleted).isTrue()
  }

  @Test
  fun `entering the alternate screen shows the alternate buffer editor`() = doTest { fixture ->
    val view = fixture.view

    fixture.enterAlternateScreen()

    val editor = fixture.activeEditor
    assertThat(editor).isNotSameAs(view.outputEditor)
    assertThat(view.preferredFocusableComponent).isSameAs(editor.contentComponent)
    assertThat(editor.document).isSameAs((view.outputModels.alternative as MutableTerminalOutputModel).document)
  }

  @Test
  fun `leaving the alternate screen shows the output editor with its content`() = doTest { fixture ->
    val view = fixture.view
    fixture.connector.feed("user@host:~$ vim")
    fixture.assertOutputModelState(view.outputModels.regular) { it.text.contains("vim") }

    fixture.enterAlternateScreen(sameChunkText = "~ VIM ~")
    fixture.leaveAlternateScreen()

    assertThat(view.preferredFocusableComponent).isSameAs(view.outputEditor.contentComponent)
    assertThat(view.outputEditor.document.text.trimEnd()).isEqualTo("user@host:~$ vim")
  }

  @Test
  fun `a second switch to the alternate screen reuses the alternate buffer editor`() = doTest { fixture ->
    fixture.enterAlternateScreen()
    val firstEditor = fixture.activeEditor
    fixture.leaveAlternateScreen()

    fixture.enterAlternateScreen()

    assertThat(fixture.activeEditor).isSameAs(firstEditor)
  }

  @Test
  fun `content in the same chunk as the switch is shown in the alternate buffer editor`() = doTest { fixture ->
    val alternate = fixture.view.outputModels.alternative

    // The alternate model gets this content before the view switches the editors.
    fixture.enterAlternateScreen(sameChunkText = "~ VIM ~")
    fixture.assertOutputModelState(alternate) {
      it.text.contains("~ VIM ~") && it.cursorOffset == it.startOffset + 7L
    }

    val editor = fixture.activeEditor
    assertThat(editor.document.text.trimEnd()).isEqualTo("~ VIM ~")
    val cursorHighlighters = editor.markupModel.allHighlighters.filter {
      it.isValid && it.layer == HighlighterLayer.LAST && it.customRenderer != null
    }
    assertThat(cursorHighlighters.map { it.startOffset }).containsExactly(7)
  }

  @Test
  fun `the switch to the alternate screen finishes the search session`() = doTest { fixture ->
    val view = fixture.view
    val panel = view.component as UiDataProvider
    val dataContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink -> panel.uiDataSnapshot(sink) }
    val searchController = checkNotNull(dataContext.getData(TerminalSearchController.KEY)) { "No search controller in the data context" }
    searchController.startOrActivateSearchSession(view.outputEditor)
    check(searchController.hasActiveSession()) { "Setup failed: the search session did not start" }

    fixture.enterAlternateScreen()

    assertThat(searchController.hasActiveSession()).isFalse()
  }

  @Test
  fun `a view that connects in the alternate screen shows the restored alternate buffer content`() = doTest { fixture ->
    fixture.enterAlternateScreen(sameChunkText = "~ VIM ~")
    fixture.assertOutputModelState(fixture.view.outputModels.alternative) { it.text.contains("~ VIM ~") }

    // The session starts each new output flow with a snapshot of its state, as for a reconnected client.
    val scope = terminalProjectScope(project).childScope("Second TerminalViewImpl")
    try {
      val secondView = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, scope)
      secondView.connectToSession(fixture.session)
      secondView.outputModels.active.first { it === secondView.outputModels.alternative }

      val secondEditor = checkNotNull(secondView.alternateBufferEditorDeferred.getNow())
      assertThat(secondView.preferredFocusableComponent).isSameAs(secondEditor.contentComponent)
      assertThat(secondView.outputModels.alternative.text.trimEnd()).isEqualTo("~ VIM ~")
    }
    finally {
      scope.cancel()
    }
  }
}

/** Enters the alternate screen with [sameChunkText] in the same chunk, and waits until the view shows the alternate buffer. */
private suspend fun TerminalViewFixture.enterAlternateScreen(sameChunkText: String = "") {
  connector.feed("${ESC}[?1049h$sameChunkText")
  // The switch listener of the view changes the active model after it shows the editor.
  view.outputModels.active.first { it === view.outputModels.alternative }
}

/** Leaves the alternate screen and waits until the view shows the output buffer. */
private suspend fun TerminalViewFixture.leaveAlternateScreen() {
  connector.feed("${ESC}[?1049l")
  view.outputModels.active.first { it === view.outputModels.regular }
}
