package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

abstract public class PostfixTemplateTestCase extends LightPlatformCodeInsightFixtureTestCase {
  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }
}
