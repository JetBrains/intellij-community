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
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
internal class TerminalCompletionInsertionTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  val testCommandSpec = ShellCommandSpec("test_cmd") {
    subcommands {
      subcommand("status") {
        argument {
          suggestions("single-suggestion")
        }
      }
      subcommand("stop") {
        argument {
          suggestions("platform/", "platform-ui/", "shared\\", "shared-ui\\")
        }
      }
      subcommand("start") {
        argument {
          suggestions("app", "application")
        }
      }
      subcommand("with-cursor-single") {
        argument {
          suggestions {
            listOf(ShellCompletionSuggestion("suggestionAfter") { insertValue("suggestion{cursor}After") })
          }
        }
      }
      subcommand("with-cursor-double") {
        argument {
          suggestions {
            listOf(
              ShellCompletionSuggestion("suggestion"),
              ShellCompletionSuggestion("suggestionAfter") {
                priority(100)
                insertValue("suggestion{cursor}After")
              }
            )
          }
        }
      }
      subcommand("custom-insert-value") {
        argument {
          suggestions {
            listOf(
              ShellCompletionSuggestion("files") {
                priority(100)
                insertValue("someCustomInsertValue")
              },
              ShellCompletionSuggestion("figures")
            )
          }
        }
      }
    }
  }

  @Test
  fun `test completion item inserted on insert suggestion action`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("start", "status", "stop"))

    val selectedItem = lookup.currentItem ?: error("No selected item")
    fixture.insertSelectedItem()

    val expectedText = "test_cmd ${selectedItem.lookupString}"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong())
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  @Test
  fun `test single matching completion item inserted automatically on invoking completion action`() = doTest { fixture ->
    fixture.type("test_cmd status single")
    fixture.callCompletionPopup(waitForPopup = false)

    val expectedText = "test_cmd status single-suggestion"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong())
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  @Test
  fun `test Enter key event is sent after inserting fully matching completion item`() = doTest { fixture ->
    fixture.type("test_cmd start a")
    fixture.callCompletionPopup()
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("app", "application"))

    fixture.type("pp")
    fixture.insertSelectedItem()

    val expectedText = "test_cmd start app\n"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong())
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  @Test
  fun `test Enter key event is sent after inserting directory name without file separator (Unix)`() = doTest { fixture ->
    fixture.type("test_cmd stop plat")
    fixture.callCompletionPopup()
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("platform/", "platform-ui/"))

    fixture.type("form")
    fixture.insertSelectedItem()

    val expectedText = "test_cmd stop platform/\n"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong())
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  @Test
  fun `test Enter key event is sent after inserting directory name without file separator (Windows)`() {
    doTest(isPowerShell = true) { fixture ->
      fixture.type("test_cmd stop sha")
      fixture.callCompletionPopup()
      val lookup = fixture.getActiveLookup() ?: error("No active lookup")
      assertThat(lookup.items.map { it.lookupString })
        .hasSameElementsAs(listOf("shared\\", "shared-ui\\"))

      fixture.type("red")
      fixture.insertSelectedItem()

      val expectedText = "test_cmd stop shared\\\n"
      val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong())
      fixture.assertOutputModelState(expectedText, expectedCursorOffset)
    }
  }

  @Test
  fun `test cursor placed correctly after inserting suggestion with custom cursor position`() = doTest { fixture ->
    fixture.type("test_cmd with-cursor-double sugg")
    fixture.callCompletionPopup()
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("suggestion", "suggestionAfter"))
    assertThat(lookup.currentItem?.lookupString)
      .isEqualTo("suggestionAfter")

    fixture.insertSelectedItem()

    val expectedText = "test_cmd with-cursor-double suggestionAfter"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong() - 5) // cursor is before 'After'
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  @Test
  fun `test cursor placed correctly after auto-inserting single suggestion with custom cursor position`() = doTest { fixture ->
    fixture.type("test_cmd with-cursor-single sugg")
    fixture.callCompletionPopup(waitForPopup = false)

    val expectedText = "test_cmd with-cursor-single suggestionAfter"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong() - 5) // cursor is before 'After'
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  @Test
  fun `test suggestion name is used for prefix matching but custom insert value is inserted`() = doTest { fixture ->
    fixture.type("test_cmd custom-insert-value fi")
    fixture.callCompletionPopup()
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("figures", "files"))
    assertThat(lookup.currentItem?.lookupString)
      .isEqualTo("files")

    fixture.insertSelectedItem()

    val expectedText = "test_cmd custom-insert-value someCustomInsertValue"
    val expectedCursorOffset = TerminalOffset.of(expectedText.length.toLong())
    fixture.assertOutputModelState(expectedText, expectedCursorOffset)
  }

  private suspend fun TerminalCompletionFixture.assertOutputModelState(
    expectedText: String,
    expectedCursorOffset: TerminalOffset,
  ) {
    val conditionMet = awaitOutputModelState(3.seconds) { model ->
      val text = model.getText(model.startOffset, model.endOffset).toString()
      expectedText == text && model.cursorOffset == expectedCursorOffset
    }

    val model = outputModel
    assertThat(conditionMet)
      .overridingErrorMessage {
        """
        Output model text doesn't match expected text.
        Current text: '${model.getText(model.startOffset, model.endOffset)}', cursor offset: ${model.cursorOffset}
        Expected text: '$expectedText', cursor offset: $expectedCursorOffset
      """.trimIndent()
      }
      .isTrue
  }

  private fun doTest(isPowerShell: Boolean = false, block: suspend (TerminalCompletionFixture) -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val fixtureScope = childScope("TerminalCompletionFixture")
      val startupOptions = TerminalStartupOptionsImpl(
        shellCommand = if (isPowerShell) listOf("powershell.exe") else listOf("/bin/zsh", "--login", "-i"),
        workingDirectory = "fakeDir",
        envVariables = emptyMap(),
        pid = null,
      )
      val session = EchoingTerminalSession(startupOptions, fixtureScope.childScope("EchoingTerminalSession"))
      doWithCompletionFixture(project, session, fixtureScope) { fixture ->
        fixture.mockTestShellCommand(testCommandSpec)
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
}