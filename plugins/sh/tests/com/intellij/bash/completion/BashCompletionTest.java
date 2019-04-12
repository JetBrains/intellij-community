package com.intellij.bash.completion;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class BashCompletionTest extends LightCodeInsightFixtureTestCase {
  public void testWordsCompletion() {
    myFixture.configureByText("a.sh", "echo\nech<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("echo\necho <caret>");
  }
}
