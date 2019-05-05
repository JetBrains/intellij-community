package com.intellij.bash.lexer;

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
