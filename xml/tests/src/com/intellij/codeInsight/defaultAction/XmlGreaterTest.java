// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class XmlGreaterTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(XmlTestUtil.getXmlTestDataPath());
  }

  @Test
  public void testHtmlGreater() {
    String path = "codeInsight/defaultAction/greater/";

    configureByFile(path + "1.html");
    performAction('>');
    checkResultByFile(path + "1_after.html");

    configureByFile(path + "2.html");
    performAction('>');
    checkResultByFile(path + "2_after.html");

    configureByFile(path + "3.html");
    performAction('>');
    checkResultByFile(path + "3_after.html");

    configureByFile(path + "4.html");
    performAction('>');
    checkResultByFile(path + "4_after.html");

    configureByFile(path + "5.html");
    performAction('>');
    checkResultByFile(path + "5_after.html");

    configureByFile(path + "6.html");
    performAction('>');
    checkResultByFile(path + "6_after.html");

    configureByFile(path + "7.html");
    performAction('>');
    checkResultByFile(path + "7_after.html");
  }

  @Test
  public void testXHtmlGreater() {
    String path = "codeInsight/defaultAction/greater/";

    configureByFile(path + "1.xhtml");
    performAction('>');
    checkResultByFile(path + "1_after.xhtml");
  }

  @Test
  public void testXmlGreater() {
    String path = "codeInsight/defaultAction/greater/";

    configureByFile(path + "1.xml");
    performAction('>');
    checkResultByFile(path + "1_after.xml");

    configureByFile(path + "2.xml");
    performAction('>');
    checkResultByFile(path + "2_after.xml");

    configureByFile(path + "3.xml");
    performAction('>');
    checkResultByFile(path + "3_after.xml");

    configureByFile(path + "4.xml");
    performAction('>');
    checkResultByFile(path + "4_after.xml");

    configureByFile(path + "5.xml");
    performAction('>');
    checkResultByFile(path + "5_after.xml");

    configureByFile(path + "6.xml");
    performAction('>');
    checkResultByFile(path + "6_after.xml");

    configureByFile(path + "7.xml");
    performAction('>');
    checkResultByFile(path + "7_after.xml");

    configureByFile(path + "8.xml");
    performAction('>');
    checkResultByFile(path + "8_after.xml");

    configureByFile(path + "9.xml");
    performAction('>');
    checkResultByFile(path + "9_after.xml");

    configureByFile(path + "10.xml");
    performAction('>');
    checkResultByFile(path + "10_after.xml");

    configureByFile(path + "11.xml");
    performAction('>');
    checkResultByFile(path + "11_after.xml");

    configureByFile(path + "12.xml");
    performAction('>');
    checkResultByFile(path + "12_after.xml");

    configureByFile(path + "12_2.xml");
    performAction('>');
    checkResultByFile(path + "12_2_after.xml");

    configureByFile(path + "12_3.xml");
    performAction('>');
    checkResultByFile(path + "12_3_after.xml");

    configureByFile(path + "13.xml");
    performAction('>');
    checkResultByFile(path + "13_after.xml");
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
