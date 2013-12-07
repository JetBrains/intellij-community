package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

abstract public class PostfixTemplateTestCase extends LightCodeInsightFixtureTestCase {
  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package java.lang;\n" +
                       "public final class Boolean implements java.io.Serializable,\n" +
                       "                                      Comparable<Boolean>\n" +
                       "{}");
  }
}
