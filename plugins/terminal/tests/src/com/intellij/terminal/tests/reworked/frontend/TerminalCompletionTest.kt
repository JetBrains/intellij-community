package com.intellij.terminal.tests.reworked.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
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

}