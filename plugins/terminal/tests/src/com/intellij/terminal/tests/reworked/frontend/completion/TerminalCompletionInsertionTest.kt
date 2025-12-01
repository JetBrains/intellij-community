package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
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
      subcommand("status")
      subcommand("stop")
      subcommand("start")
    }
  }

  @Test
  fun `test completion item inserted on insert suggestion action`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

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

  private suspend fun createFixture(): TerminalCompletionFixture {
    val fixture = TerminalCompletionFixture(project, testRootDisposable)
    fixture.mockTestShellCommand(testCommandSpec)
    fixture.awaitShellIntegrationFeaturesInitialized()
    return fixture
  }
}