// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ShHighlightingLexerTest extends LightCodeInsightFixtureTestCase {
  public void testLexerHighlighting() {
    myFixture.configureByText("a.sh",
        "for (( i=0; i<1; i++ )) do\n" +
        "     echo 1\n" +
        "done");

    myFixture.checkHighlighting();
  }
}
