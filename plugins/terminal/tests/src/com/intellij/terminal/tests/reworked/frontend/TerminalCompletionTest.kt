package com.intellij.terminal.tests.reworked.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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
}