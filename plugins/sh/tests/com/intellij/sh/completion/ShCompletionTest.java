package com.intellij.sh.completion;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ShCompletionTest extends LightCodeInsightFixtureTestCase {
  public void testWordsCompletion() {
    myFixture.configureByText("a.sh", "echo\nech<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("echo\necho <caret>");
  }
}
