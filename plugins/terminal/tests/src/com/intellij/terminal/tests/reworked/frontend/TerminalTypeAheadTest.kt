package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadOutputModelController
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadOutputModelControllerV2
import com.intellij.terminal.tests.reworked.util.TerminalOutputPattern
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.assertMatches
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.updateContent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for the experimental Type-Ahead implementation only ([TerminalTypeAheadOutputModelControllerV2]).
 */
@RunWith(JUnit4::class)
internal class TerminalTypeAheadTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  // ==================== 1. Typing predictions ====================

  @Test
  fun `type on empty model`() = doTest { controller ->
    controller.type("hello")
    controller.model.assertMatches(outputPattern("hello<cursor>"))
  }

  @Test
  fun `type sequential chars`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.type("c")
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  @Test
  fun `type appends to existing content`() = doTest { controller ->
    controller.updateContent(contentEvent("hello<cursor>"))
    controller.type("!")
    controller.model.assertMatches(outputPattern("hello!<cursor>"))
  }

  // -- trailing spaces --

  @Test
  fun `type overwrites trailing spaces`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>   "))
    controller.type("c")
    controller.model.assertMatches(outputPattern("abc<cursor>  "))
  }

  @Test
  fun `type extends beyond trailing spaces`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor> "))
    controller.type("cde")
    controller.model.assertMatches(outputPattern("abcde<cursor>"))
  }

  // -- style inheritance --

  @Test
  fun `type inherits style from preceding alphanumeric char`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>abc</s1><cursor>"))
    controller.type("d")
    controller.model.assertMatches(outputPattern("<s1>abcd</s1><cursor>"))
  }

  @Test
  fun `type after non-alphanumeric styled char - no style`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>cmd!</s1><cursor>"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("<s1>cmd!</s1>x<cursor>"))
  }

  @Test
  fun `type after space - no style inherited`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>hello</s1> <cursor>"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("<s1>hello</s1> x<cursor>"))
  }

  @Test
  fun `type with two adjacent styles - uses style at cursor`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>hel</s1><s2>lo</s2><cursor>"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("<s1>hel</s1><s2>lox</s2><cursor>"))
  }

  @Test
  fun `type continues styled text and overwrites trailing spaces`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>hello</s1><cursor>  "))
    controller.type("x")
    controller.model.assertMatches(outputPattern("<s1>hellox</s1><cursor> "))
  }

  // -- no-op --

  @Test
  fun `type disabled - no-op`() = doTest { controller ->
    disableTypeAheadForTest()
    controller.type("a")
    controller.model.assertMatches(outputPattern("<cursor>"))
  }

  // ==================== 2. Backspace predictions ====================

  @Test
  fun `backspace removes last char and replaces with space`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("ab<cursor> "))
  }

  @Test
  fun `multiple sequential backspaces`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor>  "))
  }

  @Test
  fun `backspace removes all chars down to command start and stops`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("<cursor>   "))

    controller.backspace()
    controller.model.assertMatches(outputPattern("<cursor>   "))
  }

  // -- style handling --

  @Test
  fun `backspace removes styled char`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>abc</s1><cursor>"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("<s1>ab</s1><cursor> "))
  }

  @Test
  fun `backspace removes chars across style boundary`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>ab</s1><s2>cd</s2><cursor>"))
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("<s1>ab</s1><cursor>  "))
  }

  // -- no-op --

  @Test
  fun `backspace at command start - no-op`() = doTest { controller ->
    controller.backspace()
    controller.model.assertMatches(outputPattern("<cursor>"))
  }

  @Test
  fun `backspace at beginning of line - no-op`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("abc\n<cursor>"))

    controller.backspace()
    controller.model.assertMatches(outputPattern("abc\n<cursor>"))
  }

  @Test
  fun `backspace disabled - no-op`() = doTest { controller ->
    disableTypeAheadForTest()
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  // ==================== 3. Enter predictions ====================

  @Test
  fun `Enter on empty model - cursor moves to next line`() = doTest { controller ->
    controller.type("\n")
    controller.model.assertMatches(outputPattern("\n<cursor>"))
  }

  @Test
  fun `Enter on last line adds new line`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("abc\n<cursor>"))
  }

  @Test
  fun `Enter moves cursor to next existing line`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>\n\n"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("abc\n<cursor>\n"))
  }

  // ==================== 4. Typing with confirmation ====================

  // -- full confirmation --

  @Test
  fun `type confirmed by server`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.type("c")
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  @Test
  fun `type confirmed - server adds styles`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("<s1>a</s1><cursor>"))
    controller.model.assertMatches(outputPattern("<s1>a</s1><cursor>"))
  }

  // -- partial confirmation --

  @Test
  fun `incremental confirmation then full`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.type("c")

    controller.updateContent(contentEvent("a<cursor>"))
    controller.model.assertMatches(outputPattern("abc<cursor>"))

    controller.updateContent(contentEvent("ab<cursor>"))
    controller.model.assertMatches(outputPattern("abc<cursor>"))

    controller.updateContent(contentEvent("abc<cursor>"))
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  @Test
  fun `type after partial confirmation adds to remaining predictions`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.updateContent(contentEvent("a<cursor>"))
    controller.model.assertMatches(outputPattern("ab<cursor>"))

    controller.type("c")
    controller.model.assertMatches(outputPattern("abc<cursor>"))

    controller.updateContent(contentEvent("abc<cursor>"))
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  // -- rejection --

  @Test
  fun `type rejected - different text`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("x<cursor>"))
    controller.model.assertMatches(outputPattern("x<cursor>"))
  }

  @Test
  fun `partial text match with mismatch at end - predictions rolled back`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.type("c")
    controller.updateContent(contentEvent("abx<cursor>"))
    controller.model.assertMatches(outputPattern("abx<cursor>"))
  }

  @Test
  fun `event on next line rejects all predictions`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.updateContent(contentEvent("some other content<cursor>", startLine = 1))
    controller.model.assertMatches(outputPattern("\nsome other content<cursor>"))
  }

  // -- server lag --

  @Test
  fun `server hasn't caught up - predictions preserved`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("<cursor>"))
    controller.model.assertMatches(outputPattern("a<cursor>"))

    controller.updateContent(contentEvent("a<cursor>"))
    controller.model.assertMatches(outputPattern("a<cursor>"))
  }

  @Test
  fun `server confirms with cursor lagging behind`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.type("c")
    controller.type("d")
    controller.model.assertMatches(outputPattern("abcd<cursor>"))

    // Server writes text but cursor hasn't caught up yet
    controller.updateContent(contentEvent("a<cursor>b  "))
    controller.model.assertMatches(outputPattern("abcd<cursor>"))

    controller.updateContent(contentEvent("abcd<cursor>"))
    controller.model.assertMatches(outputPattern("abcd<cursor>"))
  }

  // -- lifecycle --

  @Test
  fun `type after confirmation starts new predictions`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("a<cursor>"))
    controller.type("b")
    controller.model.assertMatches(outputPattern("ab<cursor>"))

    controller.updateContent(contentEvent("ab<cursor>"))
    controller.model.assertMatches(outputPattern("ab<cursor>"))
  }

  @Test
  fun `type after rejection works on new content`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("x<cursor>"))
    controller.type("y")
    controller.model.assertMatches(outputPattern("xy<cursor>"))

    controller.updateContent(contentEvent("xy<cursor>"))
    controller.model.assertMatches(outputPattern("xy<cursor>"))
  }

  @Test
  fun `type after rejection inherits style from new content`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("<s1>x</s1><cursor>"))
    controller.type("y")
    controller.model.assertMatches(outputPattern("<s1>xy</s1><cursor>"))

    controller.updateContent(contentEvent("<s1>xy</s1><cursor>"))
    controller.model.assertMatches(outputPattern("<s1>xy</s1><cursor>"))
  }

  @Test
  fun `type confirmed but event has extra content`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  // -- rollback --

  @Test
  fun `rollback restores initial empty state`() = doTest { controller ->
    controller.type("a")
    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("<cursor>"))
  }

  @Test
  fun `rollback restores last stored event`() = doTest { controller ->
    controller.type("a")
    controller.type("b")

    controller.updateContent(contentEvent("<cursor>"))
    controller.updateContent(contentEvent("a<cursor>"))

    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("a<cursor>"))
  }

  @Test
  fun `rollback with no predictions - no-op`() = doTest { controller ->
    controller.updateContent(contentEvent("hello<cursor>"))
    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("hello<cursor>"))
  }

  @Test
  fun `rollback restores pre-populated model`() = doTest(
    initialContent = outputPattern("hello<cursor>")
  ) { controller ->
    controller.type("!")
    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("hello<cursor>"))
  }

  @Test
  fun `rollback preserves OSC8 links from the initial snapshot`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val controllerScope = childScope("TerminalTypeAheadController")
    try {
      val outputModel = TerminalTestUtil.createOutputModel()
      // Seed an OSC8 link before the controller is created, so it is part of the controller's initial snapshot.
      outputModel.updateContent(0L, "link here", emptyList(), listOf(Osc8Hyperlink(0L, 4L, "https://example.com")))

      val sessionModel = TerminalSessionModelImpl()
      val shellIntegration = TerminalShellIntegrationImpl(
        outputModel, sessionModel, controllerScope, LocalEelDescriptor, ShellName.of("unknown")
      )
      shellIntegration.onPromptStarted(TerminalOffset.ZERO)
      shellIntegration.onPromptFinished(TerminalOffset.ZERO)
      val controller = TerminalTypeAheadOutputModelControllerV2(
        project,
        outputModel,
        CompletableDeferred(shellIntegration),
        controllerScope,
        enableInMonolith = true,
      )

      // A prediction with no confirming server event, then a rollback that reapplies the initial snapshot.
      controller.type("!")
      controller.applyPendingUpdates()

      val links = outputModel.getOsc8Hyperlinks()
      assertEquals(1, links.size)
      assertEquals("https://example.com", links.single().uri)
    }
    finally {
      controllerScope.cancel()
    }
  }

  // -- cursor events --

  @Test
  fun `cursor event with no predictions - applied directly`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.updateCursorPosition(TerminalCursorPositionChangedEvent(0, 1))
    controller.model.assertMatches(outputPattern("a<cursor>bc"))
  }

  @Test
  fun `cursor event with active predictions - stored for rollback`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.type("d")
    controller.updateCursorPosition(TerminalCursorPositionChangedEvent(0, 1))
    // Rollback uses the stored cursor position
    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("a<cursor>bc"))
  }

  // ==================== 5. Backspace with confirmation ====================

  // -- full confirmation --

  @Test
  fun `backspace confirmed - event has space at delete position`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.updateContent(contentEvent("ab<cursor> "))
    controller.model.assertMatches(outputPattern("ab<cursor> "))
  }

  @Test
  fun `backspace confirmed - event has shorter text without trailing space`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.updateContent(contentEvent("ab<cursor>"))
    controller.model.assertMatches(outputPattern("ab<cursor>"))
  }

  @Test
  fun `backspace on space character confirmed`() = doTest { controller ->
    controller.updateContent(contentEvent("a <cursor>"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor> "))

    controller.updateContent(contentEvent("a<cursor> "))
    controller.model.assertMatches(outputPattern("a<cursor> "))
  }

  // -- incremental confirmation --

  @Test
  fun `multiple backspaces incrementally confirmed`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.backspace()

    controller.updateContent(contentEvent("ab<cursor> "))
    controller.model.assertMatches(outputPattern("a<cursor>  "))

    controller.updateContent(contentEvent("a<cursor>  "))
    controller.model.assertMatches(outputPattern("a<cursor>  "))
  }

  // -- server lag --

  @Test
  fun `backspace - server hasn't caught up`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.model.assertMatches(outputPattern("ab<cursor> "))

    controller.updateContent(contentEvent("ab<cursor> "))
    controller.model.assertMatches(outputPattern("ab<cursor> "))
  }

  // -- multi-line --

  @Test
  fun `backspace confirmed when delete position is past line content`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>\n\n"))
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor>  \n\n"))

    controller.updateContent(contentEvent("ab<cursor>\n\n"))
    controller.model.assertMatches(outputPattern("a<cursor>  \n\n"))

    controller.updateContent(contentEvent("a<cursor>\n\n"))
    controller.model.assertMatches(outputPattern("a<cursor>\n\n"))
  }

  @Test
  fun `backspace confirmed when line shrinks past multiple delete positions`() = doTest { controller ->
    controller.updateContent(contentEvent("abcd<cursor>\n\n"))
    controller.backspace()
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor>   \n\n"))

    controller.updateContent(contentEvent("a<cursor>\n\n"))
    controller.model.assertMatches(outputPattern("a<cursor>\n\n"))
  }

  // -- ghost text during confirmation --

  @Test
  fun `backspace confirmed when ghost text after cursor has different style`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>prompt </s1>abc<cursor>"))
    controller.backspace()
    controller.updateContent(contentEvent("<s1>prompt </s1>ab<cursor><s2>c</s2>"))
    controller.model.assertMatches(outputPattern("<s1>prompt </s1>ab<cursor><s2>c</s2>"))
  }

  @Test
  fun `multiple backspaces with ghost text incrementally confirmed`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>prompt </s1>ab<cursor>"))
    controller.backspace()
    controller.backspace()

    controller.updateContent(contentEvent("<s1>prompt </s1>a<cursor><s2>b</s2>"))
    controller.model.assertMatches(outputPattern("<s1>prompt </s1><cursor>  "))

    controller.updateContent(contentEvent("<s1>prompt </s1><cursor><s2>ab</s2>"))
    controller.model.assertMatches(outputPattern("<s1>prompt </s1><cursor><s2>ab</s2>"))
  }

  @Test
  fun `backspace confirmed when ghost text and nearest token are separated by whitespace`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>prompt</s1> a<cursor>"))
    controller.backspace()
    controller.backspace()

    controller.updateContent(contentEvent("<s1>prompt</s1> <cursor><s2>a</s2>"))
    controller.model.assertMatches(outputPattern("<s1>prompt</s1><cursor>  "))

    controller.updateContent(contentEvent("<s1>prompt</s1><cursor> <s2>a</s2>"))
    controller.model.assertMatches(outputPattern("<s1>prompt</s1><cursor> <s2>a</s2>"))
  }

  @Test
  fun `backspace not falsely confirmed by text after cursor with same style`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>abc</s1><cursor>"))
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("<s1>a</s1><cursor>  "))

    controller.updateContent(contentEvent("<s1>ab<cursor>c</s1>"))
    controller.model.assertMatches(outputPattern("<s1>ab<cursor>c</s1>"))
  }

  @Test
  fun `backspace not falsely confirmed by unstyled text after cursor`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>abc</s1>d<cursor>"))
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("<s1>ab</s1><cursor>  "))

    controller.updateContent(contentEvent("<s1>abc</s1><cursor>d"))
    controller.model.assertMatches(outputPattern("<s1>abc</s1><cursor>d"))
  }

  // -- rollback --

  @Test
  fun `backspace rollback restores content`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  @Test
  fun `backspace on styled content - rollback restores styles`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>abc</s1><cursor>"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("<s1>ab</s1><cursor> "))
    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("<s1>abc</s1><cursor>"))
  }

  // ==================== 6. Enter with confirmation ====================

  @Test
  fun `Enter confirmed when server moves cursor to new line`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("abc\n<cursor>"))

    controller.updateContent(contentEvent("abc\nprompt<cursor>"))
    controller.model.assertMatches(outputPattern("abc\nprompt<cursor>"))
  }

  @Test
  fun `Enter rolled back when server does not confirm`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("abc\n<cursor>"))

    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  // ==================== 7. Real-world scenarios ====================

  // -- typing and backspace cooperation --

  @Test
  fun `type then backspace removes the typed char`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor> "))
  }

  @Test
  fun `fix a typo - type, backspace, retype`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.type("c")
    controller.model.assertMatches(outputPattern("ac<cursor>"))
  }

  @Test
  fun `type and backspace all chars - cursor returns to start`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("<cursor>  "))
  }

  @Test
  fun `backspace on existing content then type replacement`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.backspace()
    controller.type("x")
    controller.model.assertMatches(outputPattern("abx<cursor>"))
  }

  @Test
  fun `backspace on styled content then type inherits correct style`() = doTest { controller ->
    controller.updateContent(contentEvent("<s1>abc</s1><cursor>"))
    controller.backspace()
    controller.type("x")
    controller.model.assertMatches(outputPattern("<s1>abx</s1><cursor>"))
  }

  @Test
  fun `type and backspace with trailing spaces`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>   "))
    controller.type("c")
    controller.backspace()
    controller.type("d")
    controller.model.assertMatches(outputPattern("abd<cursor>  "))
  }

  // -- typing and backspace with confirmation --

  @Test
  fun `type confirmed then backspace confirmed sequentially`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("a<cursor>"))

    controller.backspace()
    controller.updateContent(contentEvent("<cursor> "))
    controller.model.assertMatches(outputPattern("<cursor> "))
  }

  @Test
  fun `type then backspace - event confirms type only`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.updateContent(contentEvent("a<cursor>"))
    controller.model.assertMatches(outputPattern("a<cursor> "))

    controller.updateContent(contentEvent("a<cursor> "))
    controller.model.assertMatches(outputPattern("a<cursor> "))
  }

  @Test
  fun `type backspace type - confirmed in order`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.type("c")
    controller.model.assertMatches(outputPattern("ac<cursor>"))

    controller.updateContent(contentEvent("a<cursor>"))
    controller.model.assertMatches(outputPattern("ac<cursor>"))
    controller.updateContent(contentEvent("ab<cursor>"))
    controller.model.assertMatches(outputPattern("ac<cursor>"))
    controller.updateContent(contentEvent("a<cursor> "))
    controller.model.assertMatches(outputPattern("ac<cursor>"))
    controller.updateContent(contentEvent("ac<cursor>"))
    controller.model.assertMatches(outputPattern("ac<cursor>"))
  }

  @Test
  fun `type then backspace - all merged in single server update`() = doTest { controller ->
    controller.updateContent(contentEvent("a<cursor>"))
    controller.type("b")
    controller.type("c")
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor>  "))

    controller.updateContent(contentEvent("a<cursor>  "))
    controller.model.assertMatches(outputPattern("a<cursor>  "))
  }

  @Test
  fun `backspace on space not falsely confirmed when server has not processed it`() = doTest { controller ->
    controller.updateContent(contentEvent("a <cursor>"))
    controller.type("b")
    controller.backspace()
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor>  "))

    controller.updateContent(contentEvent("a b<cursor>"))
    controller.model.assertMatches(outputPattern("a<cursor>  "))

    controller.updateContent(contentEvent("a <cursor> "))
    controller.model.assertMatches(outputPattern("a<cursor>  "))

    controller.updateContent(contentEvent("a<cursor>  "))
    controller.model.assertMatches(outputPattern("a<cursor>  "))
  }

  @Test
  fun `backspace not yet confirmed when cursor lags behind`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.model.assertMatches(outputPattern("a<cursor> "))

    // Server writes text but cursor hasn't reached the backspace point
    controller.updateContent(contentEvent("a<cursor>b"))
    controller.model.assertMatches(outputPattern("a<cursor> "))

    controller.updateContent(contentEvent("a<cursor> "))
    controller.model.assertMatches(outputPattern("a<cursor> "))
  }

  @Test
  fun `rollback after mixed type and backspace predictions`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>"))
    controller.type("d")
    controller.backspace()
    controller.type("e")
    controller.model.assertMatches(outputPattern("abce<cursor>"))

    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("abc<cursor>"))
  }

  // -- production cycles --

  @Test
  fun `type fast with partial confirmations`() = doTest { controller ->
    controller.type("h")
    controller.type("i")

    controller.updateContent(contentEvent("h<cursor>"))
    controller.model.assertMatches(outputPattern("hi<cursor>"))

    controller.type("!")

    controller.updateContent(contentEvent("hi<cursor>"))
    controller.model.assertMatches(outputPattern("hi!<cursor>"))

    controller.updateContent(contentEvent("hi!<cursor>"))
    controller.model.assertMatches(outputPattern("hi!<cursor>"))
  }

  @Test
  fun `fix a typo mid-stream with confirmations`() = doTest { controller ->
    controller.type("h")
    controller.type("e")
    controller.type("l")

    controller.updateContent(contentEvent("h<cursor>"))
    controller.model.assertMatches(outputPattern("hel<cursor>"))

    controller.type("o")
    controller.backspace()
    controller.type("l")
    controller.type("o")
    controller.model.assertMatches(outputPattern("hello<cursor>"))

    controller.updateContent(contentEvent("he<cursor>"))
    controller.model.assertMatches(outputPattern("hello<cursor>"))

    controller.updateContent(contentEvent("hello<cursor>"))
    controller.model.assertMatches(outputPattern("hello<cursor>"))
  }

  @Test
  fun `mixed predictions rejected - model shows server content`() = doTest { controller ->
    controller.type("a")
    controller.type("b")
    controller.backspace()
    controller.updateContent(contentEvent("xyz<cursor>"))
    controller.model.assertMatches(outputPattern("xyz<cursor>"))
  }

  @Test
  fun `rejection then type and backspace on new content`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("<s1>xyz</s1><cursor>"))
    controller.type("w")
    controller.model.assertMatches(outputPattern("<s1>xyzw</s1><cursor>"))

    controller.backspace()
    controller.model.assertMatches(outputPattern("<s1>xyz</s1><cursor> "))
    controller.updateContent(contentEvent("<s1>xyz</s1><cursor> "))
    controller.model.assertMatches(outputPattern("<s1>xyz</s1><cursor> "))
  }

  @Test
  fun `type confirmed with style then backspace`() = doTest { controller ->
    controller.type("a")
    controller.updateContent(contentEvent("<s1>a</s1><cursor>"))
    controller.model.assertMatches(outputPattern("<s1>a</s1><cursor>"))

    controller.backspace()
    controller.model.assertMatches(outputPattern("<cursor> "))
    controller.updateContent(contentEvent("<cursor> "))
    controller.model.assertMatches(outputPattern("<cursor> "))
  }

  // -- multi-line scenarios --

  @Test
  fun `typing not rejected when event has trailing newlines`() = doTest { controller ->
    controller.updateContent(contentEvent("<cursor>\n\n"))
    controller.type("a")
    controller.type("b")
    controller.type("c")
    controller.model.assertMatches(outputPattern("abc<cursor>\n\n"))

    controller.updateContent(contentEvent("a<cursor>\n\n"))
    controller.model.assertMatches(outputPattern("abc<cursor>\n\n"))

    controller.updateContent(contentEvent("abc<cursor>\n\n"))
    controller.model.assertMatches(outputPattern("abc<cursor>\n\n"))
  }

  @Test
  fun `events on different lines with tentative typing`() = doTest { controller ->
    controller.updateContent(contentEvent("hello<cursor>"))
    controller.updateContent(contentEvent("abc<cursor>", startLine = 1))
    controller.model.assertMatches(outputPattern("hello\nabc<cursor>"))

    // Line changed — typing is tentative
    controller.type("d")
    controller.type("e")
    controller.type("f")
    controller.model.assertMatches(outputPattern("hello\nabc<cursor>"))

    controller.updateContent(contentEvent("hello\nabcd<cursor>"))
    controller.model.assertMatches(outputPattern("hello\nabcd<cursor>"))

    controller.backspace()
    controller.updateContent(contentEvent("abcde<cursor>", startLine = 1))
    controller.model.assertMatches(outputPattern("hello\nabcde<cursor>"))

    controller.updateContent(contentEvent("hello\nabcde<cursor> "))
    controller.model.assertMatches(outputPattern("hello\nabcde<cursor> "))
  }

  @Test
  fun `rejection on same line - multi-line content`() = doTest { controller ->
    controller.updateContent(contentEvent("output\nprompt<cursor>"))
    controller.model.assertMatches(outputPattern("output\nprompt<cursor>"))

    // Line changed — typing is tentative
    controller.type("w")
    controller.type("d")
    controller.model.assertMatches(outputPattern("output\nprompt<cursor>"))

    controller.updateContent(contentEvent("promptp<cursor>", startLine = 1))
    controller.model.assertMatches(outputPattern("output\npromptp<cursor>"))
  }

  @Test
  fun `rejection from later line applies previous event`() = doTest { controller ->
    controller.updateContent(contentEvent("\nprompt<cursor>"))

    // Line changed — typing is tentative
    controller.type("a")
    controller.type("b")
    controller.model.assertMatches(outputPattern("\nprompt<cursor>"))

    controller.updateContent(contentEvent("new content<cursor>", startLine = 2))
    controller.model.assertMatches(outputPattern("\nprompt\nnew content<cursor>"))
  }

  // ==================== 8. Tentative predictions ====================

  // -- ghost text (text after cursor) --

  @Test
  fun `type with text after cursor - model unchanged`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>cd"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("ab<cursor>cd"))
  }

  @Test
  fun `type with non-blank text beyond spaces after cursor - model unchanged`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>  x"))
    controller.type("c")
    controller.model.assertMatches(outputPattern("ab<cursor>  x"))
  }

  @Test
  fun `backspace with text after cursor - model unchanged`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>de"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("abc<cursor>de"))
  }

  @Test
  fun `backspace with non-blank text beyond spaces after cursor - model unchanged`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>  x"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("ab<cursor>  x"))
  }

  @Test
  fun `tentative typing confirmed by server`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>cd"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("ab<cursor>cd"))

    controller.updateContent(contentEvent("abx<cursor>"))
    controller.model.assertMatches(outputPattern("abx<cursor>"))
  }

  @Test
  fun `tentative typing rejected by server`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>cd"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("ab<cursor>cd"))

    controller.updateContent(contentEvent("abz<cursor>"))
    controller.model.assertMatches(outputPattern("abz<cursor>"))
  }

  @Test
  fun `tentative backspace confirmed by server`() = doTest { controller ->
    controller.updateContent(contentEvent("abc<cursor>de"))
    controller.backspace()
    controller.model.assertMatches(outputPattern("abc<cursor>de"))

    controller.updateContent(contentEvent("ab<cursor>de"))
    controller.model.assertMatches(outputPattern("ab<cursor>de"))
  }

  @Test
  fun `rollback tentative - model unchanged`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>cd"))
    controller.type("x")
    controller.type("y")
    controller.model.assertMatches(outputPattern("ab<cursor>cd"))

    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("ab<cursor>cd"))
  }

  @Test
  fun `after tentative resolved - next prediction is normal`() = doTest { controller ->
    controller.updateContent(contentEvent("ab<cursor>cd"))
    controller.type("x")
    controller.model.assertMatches(outputPattern("ab<cursor>cd"))

    controller.updateContent(contentEvent("abx<cursor>"))
    controller.model.assertMatches(outputPattern("abx<cursor>"))

    // Next prediction is normal
    controller.type("y")
    controller.model.assertMatches(outputPattern("abxy<cursor>"))
  }

  // -- line change window --

  @Test
  fun `typing after Enter - tentative during window`() = doTest { controller ->
    controller.updateContent(contentEvent("ls<cursor>"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("ls\n<cursor>"))

    controller.type("a")
    controller.type("b")
    controller.model.assertMatches(outputPattern("ls\n<cursor>"))

    controller.updateContent(contentEvent("ls\nprompt ab<cursor>"))
    controller.model.assertMatches(outputPattern("ls\nprompt ab<cursor>"))
  }

  @Test
  fun `Enter with text after cursor - tentative`() = doTest { controller ->
    controller.updateContent(contentEvent("cmd<cursor>ghost"))
    controller.type("\n")
    controller.model.assertMatches(outputPattern("cmd<cursor>ghost"))

    controller.type("a")
    controller.model.assertMatches(outputPattern("cmd<cursor>ghost"))

    controller.updateContent(contentEvent("cmd\nprompt <cursor>"))
    controller.model.assertMatches(outputPattern("cmd\nprompt <cursor>"))

    controller.updateContent(contentEvent("cmd\nprompt a<cursor>"))
    controller.model.assertMatches(outputPattern("cmd\nprompt a<cursor>"))
  }

  @Test
  fun `line change from server triggers tentative window`() = doTest { controller ->
    controller.updateContent(contentEvent("line0<cursor>"))
    controller.updateContent(contentEvent("line0\nline1<cursor>", startLine = 0))

    controller.type("a")
    controller.model.assertMatches(outputPattern("line0\nline1<cursor>"))

    controller.updateContent(contentEvent("line0\nline1a<cursor>", startLine = 0))
    controller.model.assertMatches(outputPattern("line0\nline1a<cursor>"))
  }

  @Test
  fun `tentative during window confirmed by server`() = doTest { controller ->
    controller.updateContent(contentEvent("cmd<cursor>"))
    controller.type("\n")
    controller.type("a")
    controller.model.assertMatches(outputPattern("cmd\n<cursor>"))

    // Enter confirmed, "a" still tentative — event applied
    controller.updateContent(contentEvent("cmd\nprompt <cursor>"))
    controller.model.assertMatches(outputPattern("cmd\nprompt <cursor>"))

    controller.updateContent(contentEvent("cmd\nprompt a<cursor>"))
    controller.model.assertMatches(outputPattern("cmd\nprompt a<cursor>"))
  }

  @Test
  fun `tentative during window rejected by server`() = doTest { controller ->
    controller.updateContent(contentEvent("cmd<cursor>"))
    controller.type("\n")
    controller.type("a")
    controller.model.assertMatches(outputPattern("cmd\n<cursor>"))

    controller.updateContent(contentEvent("cmd\noutput xyz<cursor>"))
    controller.model.assertMatches(outputPattern("cmd\noutput xyz<cursor>"))
  }

  @Test
  fun `rollback during line-change window - model restored`() = doTest { controller ->
    controller.updateContent(contentEvent("cmd<cursor>"))
    controller.type("\n")
    controller.type("a")
    controller.model.assertMatches(outputPattern("cmd\n<cursor>"))

    controller.applyPendingUpdates()
    controller.model.assertMatches(outputPattern("cmd<cursor>"))
  }

  // ==================== Test infrastructure ====================

  private fun doTest(
    initialContent: TerminalOutputPattern? = null,
    block: (TerminalTypeAheadOutputModelController) -> Unit,
  ) = timeoutRunBlocking(context = Dispatchers.EDT) {
    val controllerScope = childScope("TerminalTypeAheadController")
    val controller = createOutputModelController(controllerScope, initialContent)
    try {
      block(controller)
    }
    finally {
      controllerScope.cancel()
    }
  }

  private fun createOutputModelController(
    coroutineScope: CoroutineScope,
    initialContent: TerminalOutputPattern? = null,
  ): TerminalTypeAheadOutputModelController {
    val outputModel = TerminalTestUtil.createOutputModel()
    if (initialContent != null) {
      outputModel.updateContent(0, initialContent)
    }
    val sessionModel = TerminalSessionModelImpl()

    val shellIntegration = TerminalShellIntegrationImpl(
      outputModel, sessionModel, coroutineScope, LocalEelDescriptor, ShellName.of("unknown")
    )
    shellIntegration.onPromptStarted(TerminalOffset.ZERO)
    shellIntegration.onPromptFinished(TerminalOffset.ZERO)

    return TerminalTypeAheadOutputModelControllerV2(
      project,
      outputModel,
      CompletableDeferred(shellIntegration),
      coroutineScope,
      enableInMonolith = true, // tests are running in monolith mode
    )
  }

  /**
   * Builds [TerminalContentUpdatedEvent] from a pattern string.
   * Cursor defaults to the end of text when not specified in the pattern.
   */
  private fun contentEvent(
    patternString: String,
    startLine: Long = 0,
  ): TerminalContentUpdatedEvent {
    val pattern = outputPattern(patternString)
    val cursorOffset = pattern.cursorOffset ?: pattern.text.length
    val textBeforeCursor = pattern.text.substring(0, cursorOffset)
    val cursorLineOffset = textBeforeCursor.count { it == '\n' }
    val cursorCol = cursorOffset - (textBeforeCursor.lastIndexOf('\n') + 1)
    return TerminalContentUpdatedEvent(
      text = pattern.text,
      styles = pattern.styles.map { it.toDto() },
      startLineLogicalIndex = startLine,
      cursorLogicalLineIndex = startLine + cursorLineOffset,
      cursorColumnIndex = cursorCol,
    )
  }

  private fun disableTypeAheadForTest() {
    val key = "terminal.type.ahead"
    val prevValue = AdvancedSettings.getBoolean(key)
    AdvancedSettings.setBoolean(key, false)
    Disposer.register(testRootDisposable) {
      AdvancedSettings.setBoolean(key, prevValue)
    }
  }
}
