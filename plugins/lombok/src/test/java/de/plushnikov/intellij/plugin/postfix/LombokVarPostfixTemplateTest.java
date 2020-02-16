package de.plushnikov.intellij.plugin.postfix;

import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class LombokVarPostfixTemplateTest extends AbstractLombokLightCodeInsightTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testSimpleVarl() {
    doTest("/postfix/varl/");
  }

  public void testSimpleVal() {
    doTest("/postfix/val/");
  }

  protected void doTest(String pathSuffix) {
    myFixture.configureByFile(getBasePath() + pathSuffix + getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getBasePath() + pathSuffix + getTestName(true) + "_after.java", true);
  }
}
