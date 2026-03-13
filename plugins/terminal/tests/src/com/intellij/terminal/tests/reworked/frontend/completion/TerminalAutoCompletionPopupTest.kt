package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.frontend.completion.TerminalCompletionFixture.Companion.doWithCompletionFixture
import com.intellij.terminal.tests.reworked.util.EchoingTerminalSession
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalAutoCompletionPopupTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  val testCommandSpec = ShellCommandSpec("test_cmd") {
    subcommands {
      subcommand("start") {
        option("--long-opt1")
        option("--long-opt2")
      }
      subcommand("status") {
        argument {
          suggestions("server", "server-db")
        }
      }
      subcommand("stop") {
        option("-a")
        option("-A")
        option("-l")
      }
    }

    option("--some-opt")
    option("--some-other-opt")

    argument {
      suggestions("arg1", "arg2")
    }
  }

  @Test
  fun `test completion popup shows automatically on typing subcommand name (mode always)`() {
    doTest(TerminalCommandCompletionShowingMode.ALWAYS) { fixture ->
      fixture.type("test_cmd st")
      fixture.awaitNewCompletionPopupOpened()

      val lookup = fixture.getActiveLookup() ?: error("No active lookup")
      assertThat(lookup.items.map { it.lookupString })
        .hasSameElementsAs(listOf("start", "status", "stop"))
    }
  }

  @Test
  fun `test completion popup shows automatically on typing option name (mode always)`() {
    doTest(TerminalCommandCompletionShowingMode.ALWAYS) { fixture ->
      fixture.type("test_cmd start --long")
      fixture.awaitNewCompletionPopupOpened()

      val lookup = fixture.getActiveLookup() ?: error("No active lookup")
      assertThat(lookup.items.map { it.lookupString })
        .hasSameElementsAs(listOf("--long-opt1", "--long-opt2"))
    }
  }

  @Test
  fun `test completion popup shows automatically on typing argument name (mode always)`() {
    doTest(TerminalCommandCompletionShowingMode.ALWAYS) { fixture ->
      fixture.type("test_cmd status ser")
      fixture.awaitNewCompletionPopupOpened()

      val lookup = fixture.getActiveLookup() ?: error("No active lookup")
      assertThat(lookup.items.map { it.lookupString })
        .hasSameElementsAs(listOf("server", "server-db"))
    }
  }

  @Test
  fun `test completion popup doesn't show automatically in context with subcommand suggestions (mode only parameters)`() {
    doTest(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS) { fixture ->
      fixture.type("test_cmd st")
      fixture.awaitPendingRequestsProcessed()
      assertThat(fixture.isLookupActive()).isFalse()
    }
  }

  @Test
  fun `test completion popup shows automatically on typing option name (mode only parameters)`() {
    doTest(TerminalCommandCompletionShowingMode.ALWAYS) { fixture ->
      fixture.type("test_cmd start --long")
      fixture.awaitNewCompletionPopupOpened()

      val lookup = fixture.getActiveLookup() ?: error("No active lookup")
      assertThat(lookup.items.map { it.lookupString })
        .hasSameElementsAs(listOf("--long-opt1", "--long-opt2"))
    }
  }

  @Test
  fun `test completion popup shows automatically on typing argument name (mode only parameters)`() {
    doTest(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS) { fixture ->
      fixture.type("test_cmd status ser")
      fixture.awaitNewCompletionPopupOpened()

      val lookup = fixture.getActiveLookup() ?: error("No active lookup")
      assertThat(lookup.items.map { it.lookupString })
        .hasSameElementsAs(listOf("server", "server-db"))
    }
  }

  @Test
  fun `test completion popup doesn't show option suggestions automatically on typing '-'`() {
    doTest(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS) { fixture ->
      fixture.type("test_cmd stop -")
      fixture.awaitPendingRequestsProcessed()
      assertThat(fixture.isLookupActive()).isFalse()
    }
  }

  @Test
  fun `test completion popup doesn't show automatically on typing short option`() {
    doTest(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS) { fixture ->
      fixture.type("test_cmd stop -a")
      fixture.awaitPendingRequestsProcessed()
      assertThat(fixture.isLookupActive()).isFalse()
    }
  }

  private fun doTest(mode: TerminalCommandCompletionShowingMode, block: suspend (TerminalCompletionFixture) -> Unit) =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val fixtureScope = childScope("TerminalCompletionFixture")
      val startupOptions = TerminalStartupOptionsImpl(
        shellCommand = listOf("/bin/zsh", "--login", "-i"),
        workingDirectory = "fakeDir",
        envVariables = emptyMap(),
        pid = null,
      )
      val session = EchoingTerminalSession(startupOptions, fixtureScope.childScope("EchoingTerminalSession"))
      doWithCompletionFixture(project, session, fixtureScope) { fixture ->
        fixture.mockTestShellCommand(testCommandSpec)
        fixture.setCompletionOptions(
          showPopupAutomatically = true,
          showingMode = mode,
          parentDisposable = testRootDisposable
        )
        fixture.awaitShellIntegrationFeaturesInitialized()

        block(fixture)
      }
    }
}