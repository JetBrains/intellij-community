package de.plushnikov.intellij.plugin.postfix;

import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokVarPostfixTemplateTest extends AbstractLombokLightCodeInsightTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR;
  }

  public void testSimpleVarl() {
    doTest("/postfix/varl/");
  }

  public void testSimpleVal() {
    doTest("/postfix/val/");
  }

  protected void doTest(String pathSuffix) {
    myFixture.configureByFile(pathSuffix + getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(pathSuffix + getTestName(true) + "_after.java", true);
  }
}
