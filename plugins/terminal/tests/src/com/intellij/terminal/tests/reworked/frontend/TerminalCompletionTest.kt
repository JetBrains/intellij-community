package com.intellij.terminal.tests.reworked.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.KeyEvent.*

@RunWith(JUnit4::class)
class TerminalCompletionTest : BasePlatformTestCase() {

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
  fun `test completions list filtered on typing`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.mockTestShellCommand(testCommandSpec)
    terminalCompletionFixture.type("test_cmd ")
    terminalCompletionFixture.callCompletionPopup()
    assertEquals(true, terminalCompletionFixture.isLookupActive())
    val beforeResult = terminalCompletionFixture.getLookupElements()
    val beforeResultStrings = beforeResult.map { it.lookupString }
    assertSameElements(listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"),
                 beforeResultStrings)

    terminalCompletionFixture.type("s")
    val result = terminalCompletionFixture.getLookupElements()
    val resultStrings = result.map { it.lookupString }
    assertSameElements(listOf("set", "show", "start", "status", "stop", "sync"), resultStrings)
    terminalCompletionFixture.type("t")
    assertSameElements(listOf("start", "status", "stop"), terminalCompletionFixture.getLookupElements().map { it.lookupString })

  }

  @Test
  fun `test selection returns to original item after down and up actions`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.mockTestShellCommand(testCommandSpec)
    terminalCompletionFixture.type("test_cmd ")
    terminalCompletionFixture.callCompletionPopup()
    assertSameElements(listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"),
                 terminalCompletionFixture.getLookupElements().map { it.lookupString })

    val firstElement = terminalCompletionFixture.getCurrentItem()
    terminalCompletionFixture.downCompletionPopup()
    val secondElement = terminalCompletionFixture.getCurrentItem()
    terminalCompletionFixture.upCompletionPopup()
    val fistAfterElement = terminalCompletionFixture.getCurrentItem()

    assertEquals(firstElement, fistAfterElement)
    assertNotEquals(secondElement, fistAfterElement)
  }

  @Test
  fun `test completion list is re-filtered when caret moves over prefix to the left, right`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.mockTestShellCommand(testCommandSpec)
    terminalCompletionFixture.type("test_cmd ")
    terminalCompletionFixture.callCompletionPopup()

    terminalCompletionFixture.type("st")
    val startResult = terminalCompletionFixture.getLookupElements()
    assertSameElements(listOf("start", "status", "stop"), startResult.map { it.lookupString })

    terminalCompletionFixture.pressKey(VK_LEFT)
    val afterFirstLeftResult = terminalCompletionFixture.getLookupElements()
    assertSameElements(listOf("set", "show", "start", "status", "stop", "sync"),
                 afterFirstLeftResult.map { it.lookupString })
    terminalCompletionFixture.pressKey(VK_LEFT)
    val afterSecondLeftResult = terminalCompletionFixture.getLookupElements()
    assertSameElements(listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"),
                 afterSecondLeftResult.map { it.lookupString })


    terminalCompletionFixture.pressKey(VK_RIGHT)
    val afterRightResult = terminalCompletionFixture.getLookupElements()
    assertEquals(afterFirstLeftResult, afterRightResult)

    terminalCompletionFixture.pressKey(VK_RIGHT)
    val afterSecondRightResult = terminalCompletionFixture.getLookupElements()
    assertEquals(startResult, afterSecondRightResult)
  }

  @Test
  fun `test lookup remains active when caret moves into text typed before call popup`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.mockTestShellCommand(testCommandSpec)
    terminalCompletionFixture.type("test_cmd st")
    terminalCompletionFixture.callCompletionPopup()

    val startResult = terminalCompletionFixture.getLookupElements()
    assertSameElements(listOf("start", "status", "stop"),
                 startResult.map { it.lookupString })
    terminalCompletionFixture.pressKey(VK_LEFT)
    assertSameElements(listOf("set", "show", "start", "status", "stop", "sync"),
                 terminalCompletionFixture.getLookupElements().map { it.lookupString })
    terminalCompletionFixture.pressKey(VK_RIGHT)
    assertSameElements(startResult.map { it.lookupString },
                 terminalCompletionFixture.getLookupElements().map { it.lookupString })
  }

  @Test
  fun `test completion list is correctly re-filtered after pressing backspace`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.mockTestShellCommand(testCommandSpec)
    terminalCompletionFixture.type("test_cmd st")
    terminalCompletionFixture.callCompletionPopup()

    val startResult = terminalCompletionFixture.getLookupElements()
    assertSameElements(listOf("start", "status", "stop"),
                 startResult.map { it.lookupString })

    terminalCompletionFixture.type("a")
    assertSameElements(listOf("start", "status"),
                 terminalCompletionFixture.getLookupElements().map { it.lookupString })

    terminalCompletionFixture.pressKey(VK_BACK_SPACE)
    assertSameElements(listOf("start", "status", "stop"),
                 terminalCompletionFixture.getLookupElements().map { it.lookupString })

    terminalCompletionFixture.pressKey(VK_BACK_SPACE)
    assertSameElements(listOf("set", "show", "start", "status", "stop", "sync"),
                 terminalCompletionFixture.getLookupElements().map { it.lookupString })
  }

}