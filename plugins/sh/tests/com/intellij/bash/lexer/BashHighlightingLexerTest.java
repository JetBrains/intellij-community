package com.intellij.bash.lexer;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class BashHighlightingLexerTest extends LightCodeInsightFixtureTestCase {
  public void testLexerHighlighting() {
    //test #398, which had a broken lexer which broke the file highlighting with errors after new text was entered

    myFixture.configureByText("a.sh", "$(<caret>)");

    myFixture.type("$");
    myFixture.type("{");
    myFixture.type("1");
    myFixture.type("}"); //typing these characters resulted in lexer exceptions all over the place
  }
}
