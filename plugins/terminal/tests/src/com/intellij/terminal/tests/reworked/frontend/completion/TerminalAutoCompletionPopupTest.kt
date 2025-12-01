package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
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
  fun `test completion popup shows automatically on typing subcommand name (mode always)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ALWAYS)

    fixture.type("test_cmd st")
    fixture.awaitNewCompletionPopupOpened()

    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("start", "status", "stop"))
    Unit
  }

  @Test
  fun `test completion popup shows automatically on typing option name (mode always)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ALWAYS)

    fixture.type("test_cmd start --long")
    fixture.awaitNewCompletionPopupOpened()

    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("--long-opt1", "--long-opt2"))
    Unit
  }

  @Test
  fun `test completion popup shows automatically on typing argument name (mode always)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ALWAYS)

    fixture.type("test_cmd status ser")
    fixture.awaitNewCompletionPopupOpened()

    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("server", "server-db"))
    Unit
  }

  @Test
  fun `test completion popup doesn't show automatically in context with subcommand suggestions (mode only parameters)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS)

    fixture.type("test_cmd st")
    delay(2000)
    assertThat(fixture.isLookupActive()).isFalse()
    Unit
  }

  @Test
  fun `test completion popup shows automatically on typing option name (mode only parameters)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ALWAYS)

    fixture.type("test_cmd start --long")
    fixture.awaitNewCompletionPopupOpened()

    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("--long-opt1", "--long-opt2"))
    Unit
  }

  @Test
  fun `test completion popup shows automatically on typing argument name (mode only parameters)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS)

    fixture.type("test_cmd status ser")
    fixture.awaitNewCompletionPopupOpened()

    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("server", "server-db"))
    Unit
  }

  @Test
  fun `test completion popup doesn't show option suggestions automatically on typing '-'`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS)

    fixture.type("test_cmd stop -")
    delay(2000)
    assertThat(fixture.isLookupActive()).isFalse()
    Unit
  }

  @Test
  fun `test completion popup doesn't show automatically on typing short option`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture(TerminalCommandCompletionShowingMode.ONLY_PARAMETERS)

    fixture.type("test_cmd stop -a")
    delay(2000)
    assertThat(fixture.isLookupActive()).isFalse()
    Unit
  }

  private suspend fun createFixture(mode: TerminalCommandCompletionShowingMode): TerminalCompletionFixture {
    val fixture = TerminalCompletionFixture(project, testRootDisposable)
    fixture.mockTestShellCommand(testCommandSpec)
    fixture.setCompletionOptions(
      showPopupAutomatically = true,
      showingMode = mode,
      parentDisposable = testRootDisposable
    )
    fixture.awaitShellIntegrationFeaturesInitialized()
    return fixture
  }
}