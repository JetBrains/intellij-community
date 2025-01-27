// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked;

import com.intellij.openapi.editor.LineWrapPositionStrategy;
import com.intellij.openapi.editor.impl.SoftWrapEngine;
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalLineWrapPositionStrategy;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class TerminalLineWrapPositionStrategyTest {
  private final LineWrapPositionStrategy myStrategy= new TerminalLineWrapPositionStrategy();;

  //uD852 and uDF62 are surrogate pair - 2 characters that made 1 "𤭢"; soft wrap shouldn't be made between them
  //the expected result is to move the 3rd symbol to the next line, so we will have  uD852 uDF62\n uD852 uDF62 = 𤭢\n𤭢
  @Test
  public void preventWrapInsideOfSurrogatePairs() {
    String text = "\uD852\uDF62\uD852\uDF62";//𤭢𤭢
    int expectedResult = 3;
    int offset = SoftWrapEngine.findWrapPosition(text, text.length() - 1, 1, myStrategy);
    assertSame(expectedResult, offset);
  }
}
