package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.editor.impl.SoftWrapEngine
import com.intellij.terminal.frontend.TerminalLineWrapPositionStrategy
import org.junit.Assert
import org.junit.Test

internal class TerminalLineWrapPositionStrategyTest {
  //uD852 and uDF62 are surrogate pair - 2 characters that made 1 "𤭢"; soft wrap shouldn't be made between them
  //the expected result is to move the 3rd symbol to the next line, so we will have  uD852 uDF62\n uD852 uDF62 = 𤭢\n𤭢
  @Test
  fun preventWrapInsideOfSurrogatePairs() {
    val strategy = TerminalLineWrapPositionStrategy()
    val text = "\uD852\uDF62\uD852\uDF62" //𤭢𤭢
    val expectedResult = 3
    val offset = SoftWrapEngine.findWrapPosition(text, text.length - 1, 1, strategy)
    Assert.assertSame(expectedResult, offset)
  }
}