package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.KeyEvent.*

@RunWith(JUnit4::class)
class TerminalCompletionPopupTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  val testCommandSpec = ShellCommandSpec("test_cmd") {
    subcommands {
      subcommand("status")
      subcommand("stop")
      subcommand("set")
      subcommand("sync")
      subcommand("show")

      subcommand("start") {
        argument {
          isOptional = true
          suggestions("platform/", "platform-ui/", "shared\\", "shared-ui\\")
        }
      }
    }

    subcommands {
      subcommand("build")
      subcommand("bind")
      subcommand("branch")
    }
  }

  @Test
  fun `test completions list filtered on typing`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd ")
    fixture.callCompletionPopup()
    assertEquals(true, fixture.isLookupActive())
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"))

    fixture.type("s")
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))

    fixture.type("t")
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))
  }

  @Test
  fun `test selection returns to original item after down and up actions`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd ")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"))

    val firstElement = fixture.getCurrentItem()
    fixture.downCompletionPopup()
    val secondElement = fixture.getCurrentItem()
    fixture.upCompletionPopup()
    val firstAfterElement = fixture.getCurrentItem()

    assertEquals(firstElement, firstAfterElement)
    assertNotEquals(secondElement, firstAfterElement)
  }

  @Test
  fun `test completion list is re-filtered when caret moves over prefix to the left, right`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd ")
    fixture.callCompletionPopup()

    fixture.type("sta")
    val startResult = fixture.getLookupElements()
    assertSameElements(startResult.map { it.lookupString }, listOf("start", "status"))

    fixture.pressKey(VK_LEFT)
    val afterFirstLeftResult = fixture.getLookupElements()
    assertSameElements(afterFirstLeftResult.map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.pressKey(VK_LEFT)
    val afterSecondLeftResult = fixture.getLookupElements()
    assertSameElements(afterSecondLeftResult.map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))

    fixture.pressKey(VK_RIGHT)
    val afterRightResult = fixture.getLookupElements()
    assertEquals(afterFirstLeftResult, afterRightResult)

    fixture.pressKey(VK_RIGHT)
    val afterSecondRightResult = fixture.getLookupElements()
    assertEquals(startResult, afterSecondRightResult)
  }

  @Test
  fun `test lookup remains active when caret moves into text typed before call popup`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    val startResult = fixture.getLookupElements().map { it.lookupString }
    assertSameElements(startResult, listOf("start", "status", "stop"))
    fixture.pressKey(VK_LEFT)
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))
    fixture.pressKey(VK_RIGHT)
    assertSameElements(fixture.getLookupElements().map { it.lookupString }, startResult)
  }

  @Test
  fun `test completion list is correctly re-filtered after pressing backspace`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.type("a")
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status"))

    fixture.pressKey(VK_BACK_SPACE)
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.pressKey(VK_BACK_SPACE)
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))
  }

  @Test
  fun `test completion popup closes on empty prefix after pressing backspace`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    fixture.pressKey(VK_BACK_SPACE)
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))

    fixture.pressKey(VK_BACK_SPACE)
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes on empty prefix after pressing left`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    fixture.pressKey(VK_LEFT)
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))

    fixture.pressKey(VK_LEFT)
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes on non-matching prefix`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.type("x")
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes when any text appears below the line with cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.outputModel.update(1, "some text")
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test exact match for directory item is placed first (Unix separator)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd start plat")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("platform/", "platform-ui/"))
    fixture.type("form")

    val firstElement = fixture.getCurrentItem()
    assertEquals("platform/", firstElement?.lookupString)
  }

  @Test
  fun `test exact match for directory item is placed first (Windows separator)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd start sha")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("shared\\", "shared-ui\\"))
    fixture.type("red")

    val firstElement = fixture.getCurrentItem()
    assertEquals("shared\\", firstElement?.lookupString)
  }

  @Test
  fun `test terminal LookupElement#object is ShellCompletionSuggestion`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    val elements = fixture.getLookupElements()
    assertThat(elements)
      .isNotEmpty
      .allMatch { it.`object` is ShellCompletionSuggestion }
    Unit
  }

  @Test
  fun `test TerminalCommandCompletion#COMPLETING_COMMAND_KEY is set in lookup when completion popup is shown`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items).isNotEmpty
    assertThat(lookup.getUserData(TerminalCommandCompletion.COMPLETING_COMMAND_KEY))
      .isEqualTo("test_cmd st")
    Unit
  }

  @Test
  fun `test TerminalCommandCompletion#LAST_SELECTED_ITEM_KEY is updated in the lookup on selected item change`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    // Check that the initial selected item value is set
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("start", "status", "stop"))
    val firstSelectedItem = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY)
    assertThat(firstSelectedItem).isNotNull

    // Narrow down the prefix and check that the selected item value is updated
    fixture.type("o")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("stop"))
    val secondSelectedItem = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY)
    assertThat(secondSelectedItem)
      .isNotNull
      .matches { it?.lookupString == "stop" }
      .isNotEqualTo(firstSelectedItem)

    // Type an unrelated prefix to close the popup and check that the last selected item stay the same.
    fixture.type(" ")
    assertThat(lookup.isLookupDisposed).isTrue
    val lastSelectedItem = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY)
    assertThat(lastSelectedItem)
      .isNotNull
      .isEqualTo(secondSelectedItem)

    Unit
  }

  private suspend fun createFixture(): TerminalCompletionFixture {
    val fixture = TerminalCompletionFixture(project, testRootDisposable)
    fixture.mockTestShellCommand(testCommandSpec)
    fixture.awaitShellIntegrationFeaturesInitialized()
    return fixture
  }
}