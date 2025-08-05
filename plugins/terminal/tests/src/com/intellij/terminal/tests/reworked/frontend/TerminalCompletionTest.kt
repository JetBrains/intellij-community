package com.intellij.terminal.tests.reworked.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT

@RunWith(JUnit4::class)
class TerminalCompletionTest : BasePlatformTestCase() {

  @Test
  fun `test completions list filtered on typing`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.type("git s")
    terminalCompletionFixture.callCompletionPopup()
    assertEquals(true, terminalCompletionFixture.isLookupActive())
    val beforeResult = terminalCompletionFixture.getLookupElements()
    val beforeResultStrings = beforeResult.map { it.lookupString }
    assertEquals(listOf("show", "stage", "stash", "status", "submodule", "switch"), beforeResultStrings)

    terminalCompletionFixture.type("t")
    val result = terminalCompletionFixture.getLookupElements()
    val resultStrings = result.map { it.lookupString }
    assertEquals(listOf("stage", "stash", "status"), resultStrings)
  }

  @Test
  fun `test terminal completion up and down`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.type("git b")
    terminalCompletionFixture.callCompletionPopup()
    val startResult = terminalCompletionFixture.getLookupElements()
    assertEquals(3, startResult.size)

    val firstElement = terminalCompletionFixture.getCurrentItem()
    terminalCompletionFixture.downCompletionPopup()
    val secondElement = terminalCompletionFixture.getCurrentItem()
    terminalCompletionFixture.upCompletionPopup()
    val fistAfterElement = terminalCompletionFixture.getCurrentItem()

    assertEquals(firstElement, fistAfterElement)
    assertNotEquals(secondElement, fistAfterElement)
  }

  @Test
  fun `test terminal completion with left right`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.type("git ")
    terminalCompletionFixture.callCompletionPopup()

    terminalCompletionFixture.type("st")
    val startResult = terminalCompletionFixture.getLookupElements()

    terminalCompletionFixture.pressKey(VK_LEFT)
    val afterFirstLeftResult = terminalCompletionFixture.getLookupElements()
    terminalCompletionFixture.pressKey(VK_LEFT)

    val afterSecondLeftResult = terminalCompletionFixture.getLookupElements()
    assertNotEquals(afterFirstLeftResult.size, afterSecondLeftResult.size)

    terminalCompletionFixture.pressKey(VK_RIGHT)
    val afterRightResult = terminalCompletionFixture.getLookupElements()
    assertEquals(afterFirstLeftResult.size, afterRightResult.size)

    terminalCompletionFixture.pressKey(VK_RIGHT)
    val afterSecondRightResult = terminalCompletionFixture.getLookupElements()
    assertEquals(startResult.size, afterSecondRightResult.size)
  }

  @Test
  fun `test terminal completion with left right reopening`() {
    val terminalCompletionFixture = TerminalCompletionFixture(project, testRootDisposable)
    terminalCompletionFixture.type("git st")
    terminalCompletionFixture.callCompletionPopup()

    val startResult = terminalCompletionFixture.getLookupElements()
    assertEquals(3, startResult.size)
    terminalCompletionFixture.pressKey(VK_LEFT)
    val afterLeftResult = terminalCompletionFixture.getLookupElements()
    assertEquals(6, afterLeftResult.size)
  }

}