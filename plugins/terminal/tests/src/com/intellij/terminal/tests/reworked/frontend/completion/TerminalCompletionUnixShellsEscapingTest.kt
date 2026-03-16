package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.frontend.completion.TerminalCompletionFixture.Companion.doWithCompletionFixture
import com.intellij.terminal.tests.reworked.util.EchoingTerminalSession
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalCompletionUnixShellsEscapingTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `suggestion is inserted as is if there is a starting quote`() {
    doTest { fixture ->
      fixture.mockSuggestions(
        prefixReplacementIndex = 7,
        "with spaces",
        "dummy"
      )
      fixture.type("test_cmd '~/docs/")
      fixture.callCompletionPopup()
      fixture.insertCompletionItem("with spaces")
      fixture.assertCommandTextState("test_cmd '~/docs/with spaces<cursor>")
    }
  }

  @Test
  fun `special characters are escaped with backspaces on insertion`() {
    doTest { fixture ->
      val item = $$"with {special) $chars?"
      fixture.mockSuggestions(
        prefixReplacementIndex = 0,
        item,
        "dummy"
      )
      fixture.type("test_cmd ")
      fixture.callCompletionPopup()
      fixture.insertCompletionItem(item)
      fixture.assertCommandTextState($$"""test_cmd with\ \{special\)\ \$chars\?<cursor>""")
    }
  }

  @Test
  fun `special characters are escaped with backspaces on insertion (with additional prefix)`() {
    doTest { fixture ->
      val item = $$"with {special) $chars?"
      fixture.mockSuggestions(
        prefixReplacementIndex = 7,
        item,
        "dummy"
      )
      fixture.type("test_cmd ~/docs/")
      fixture.callCompletionPopup()
      fixture.insertCompletionItem(item)
      fixture.assertCommandTextState($$"""test_cmd ~/docs/with\ \{special\)\ \$chars\?<cursor>""")
    }
  }

  private fun doTest(block: suspend (TerminalCompletionFixture) -> Unit) = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixtureScope = childScope("TerminalCompletionFixture")
    val startupOptions = TerminalStartupOptionsImpl(
      shellCommand = listOf("/bin/zsh", "--login", "-i"),
      workingDirectory = System.getProperty("user.home"),
      envVariables = emptyMap(),
      pid = null,
    )
    val session = EchoingTerminalSession(startupOptions, fixtureScope.childScope("EchoingTerminalSession"))
    doWithCompletionFixture(project, session, fixtureScope) { fixture ->
      fixture.setCompletionOptions(
        showPopupAutomatically = false,
        showingMode = TerminalCommandCompletionShowingMode.ONLY_PARAMETERS,
        parentDisposable = testRootDisposable
      )
      fixture.awaitShellIntegrationFeaturesInitialized()

      block(fixture)
    }
  }
}