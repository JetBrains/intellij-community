package de.plushnikov.intellij.plugin.postfix;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class LombokVarPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testSimpleVarl() {
    doTest("/postfix/varl/");
  }

  @Test
  public void testSimpleVal() {
    doTest("/postfix/val/");
  }

  protected void doTest(String pathSuffix) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(pathSuffix + myFixture.getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(pathSuffix + myFixture.getTestName(true) + "_after.java", true);
  }
}
