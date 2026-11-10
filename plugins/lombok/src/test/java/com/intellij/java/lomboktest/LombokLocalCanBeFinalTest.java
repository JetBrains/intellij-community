package com.intellij.java.lomboktest;

import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class LombokLocalCanBeFinalTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private LocalCanBeFinal myTool;

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(LombokTestUtil.getTestDataPath() + "/inspection/localCanBeFinal");
    myTool = new LocalCanBeFinal();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testLombokVal() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    myTool.REPORT_IMPLICIT_FINALS = true;
    doTest();
  }
}

