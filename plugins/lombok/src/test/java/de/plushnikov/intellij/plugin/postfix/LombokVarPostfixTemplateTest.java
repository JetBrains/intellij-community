package de.plushnikov.intellij.plugin.postfix;

import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class LombokVarPostfixTemplateTest extends AbstractLombokLightCodeInsightTestCase {

  public void testSimpleVarl() {
    doTest("/postfix/varl/");
  }

  public void testSimpleVal() {
    doTest("/postfix/val/");
  }

  protected void doTest(String pathSuffix) {
    myFixture.configureByFile( pathSuffix + getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(pathSuffix + getTestName(true) + "_after.java", true);
  }
}
