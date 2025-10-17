package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
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
      subcommand("start")
      subcommand("stop")
      subcommand("set")
      subcommand("sync")
      subcommand("show")
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
  fun `test completion popup closes when any text appears below the line with cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()

    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.outputModel.update(1, "some text")
    assertFalse(fixture.isLookupActive())
  }

  private suspend fun createFixture(): TerminalCompletionFixture {
    val fixture = TerminalCompletionFixture(project, testRootDisposable)
    fixture.mockTestShellCommand(testCommandSpec)
    fixture.awaitShellIntegrationFeaturesInitialized()
    return fixture
  }
}