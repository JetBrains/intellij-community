package com.intellij.codeInsight.defaultAction;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class XmlSlashTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(XmlTestUtil.getXmlTestDataPath());
  }

  @Test
  public void testXmlSlash() {
    String path = "codeInsight/defaultAction/slash/";

    configureByFile(path + "1.xml");
    performAction('/');
    checkResultByFile(path + "1_after.xml");

    configureByFile(path + "2.xml");
    performAction('/');
    checkResultByFile(path + "2_after.xml");

    configureByFile(path + "3.xml");
    performAction('/');
    checkResultByFile(path + "3_after.xml");

    configureByFile(path + "4.xml");
    performAction('/');
    checkResultByFile(path + "4_after.xml");
  }

  @Test
  public void testHtmlSlash() {
    String path = "codeInsight/defaultAction/slash/";

    configureByFile(path + "1.html");
    performAction('/');
    checkResultByFile(path + "1_after.html");
  }

  private void configureByFile(String filePath) {
    codeInsightFixture.get().configureByFile(filePath);
  }

  private void performAction(char c) {
    DefaultActionTestUtil.performAction(codeInsightFixture.get(), c);
  }

  private void checkResultByFile(String filePath) {
    codeInsightFixture.get().checkResultByFile(filePath);
  }
}
