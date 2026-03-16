package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.frontend.completion.TerminalCompletionFixture.Companion.doWithCompletionFixture
import com.intellij.terminal.tests.reworked.util.EchoingTerminalSession
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.KeyEvent

@RunWith(JUnit4::class)
internal class TerminalCompletionPowerShellEscapingTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `item is surrounded with single quotes`() {
    doTest { fixture ->
      fixture.mockSuggestions(
        prefixReplacementIndex = 0,
        "with spaces",
        "dummy"
      )
      fixture.type("test_cmd ")
      fixture.callCompletionPopup()
      fixture.insertCompletionItem("with spaces")
      fixture.assertCommandTextState("test_cmd 'with spaces<cursor>'")
    }
  }

  @Test
  fun `whole token is surrounded with single quotes`() {
    doTest { fixture ->
      fixture.mockSuggestions(
        prefixReplacementIndex = 3,
        "with spaces",
        "dummy"
      )
      fixture.type("test_cmd C:/")
      fixture.callCompletionPopup()
      fixture.insertCompletionItem("with spaces")
      fixture.assertCommandTextState("test_cmd 'C:/with spaces<cursor>'")
    }
  }

  @Test
  fun `suggestion is inserted as is if there is a starting quote`() {
    doTest { fixture ->
      fixture.mockSuggestions(
        prefixReplacementIndex = 3,
        "with spaces",
        "dummy"
      )
      fixture.type("test_cmd 'C:/")
      fixture.callCompletionPopup()
      fixture.insertCompletionItem("with spaces")
      fixture.assertCommandTextState("test_cmd 'C:/with spaces<cursor>")
    }
  }

  @Test
  fun `closing quote is not duplicated when starting with no quote`() {
    doTest { fixture ->
      fixture.mockSuggestions(
        prefixReplacementIndex = 3,
        "with spaces",
        "dummy"
      )
      fixture.type("test_cmd C:/'")
      fixture.pressKey(KeyEvent.VK_LEFT)
      fixture.callCompletionPopup()
      fixture.insertCompletionItem("with spaces")
      fixture.assertCommandTextState("test_cmd 'C:/with spaces<cursor>'")
    }
  }

  @Test
  fun `closing quote is not duplicated when starting with quote`() {
    doTest { fixture ->
      fixture.mockSuggestions(
        prefixReplacementIndex = 3,
        "with spaces",
        "dummy"
      )
      fixture.type("test_cmd 'C:/'")
      fixture.pressKey(KeyEvent.VK_LEFT)
      fixture.callCompletionPopup()
      fixture.insertCompletionItem("with spaces")
      fixture.assertCommandTextState("test_cmd 'C:/with spaces<cursor>'")
    }
  }

  private fun doTest(block: suspend (TerminalCompletionFixture) -> Unit) = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixtureScope = childScope("TerminalCompletionFixture")
    val startupOptions = TerminalStartupOptionsImpl(
      shellCommand = listOf("powershell.exe"),
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

internal fun TerminalCompletionFixture.mockSuggestions(
  prefixReplacementIndex: Int,
  vararg toSuggest: String,
) {
  val spec = ShellCommandSpec("test_cmd") {
    argument {
      suggestions {
        toSuggest.map {
          ShellCompletionSuggestion(it) {
            prefixReplacementIndex(prefixReplacementIndex)
          }
        }
      }
    }
  }

  mockTestShellCommand(spec)
}
