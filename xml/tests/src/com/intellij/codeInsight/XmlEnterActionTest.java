// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class XmlEnterActionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(XmlTestUtil.getXmlTestDataPath());
  }

  private void configureByFile(@NotNull String filePath) {
    codeInsightFixture.get().configureByFile(filePath);
  }

  private void checkResultByFile(@NotNull String expectedFilePath) {
    codeInsightFixture.get().checkResultByFile(expectedFilePath);
  }

  private void performAction() {
    EnterActionTestUtil.performAction(codeInsightFixture.get());
  }

  private void doTextTest(@NotNull String ext, @NotNull String before, @NotNull String after) {
    EnterActionTestUtil.doTextTest(codeInsightFixture.get(), ext, before, after);
  }

  private void checkFileTextAfterCommit() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileText = EdtTestUtil.runInEdtAndGet(() -> {
      PsiDocumentManager.getInstance(projectFixture.get()).commitDocument(myFixture.getEditor().getDocument());
      return myFixture.getFile().getText();
    });
    assertEquals(fileText, myFixture.getEditor().getDocument().getText());
  }

  @Test
  public void testInXml1() {
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "state0.xml");
    performAction();
    checkResultByFile(path + "state1.xml");
  }

  @Test
  public void testInXml2() {
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "state2.xml");
    performAction();
    checkFileTextAfterCommit();
    //checkResultByFile(path + "state3.xml");
  }

  @Test
  public void testInXml3() {
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "state4.xml");
    performAction();
    checkFileTextAfterCommit();
    //    checkResultByFile(path + "state5.xml");
  }

  @Test
  public void testInXml4() {
    CodeStyle.getSettings(projectFixture.get()).getCustomSettings(XmlCodeStyleSettings.class).XML_ALIGN_ATTRIBUTES = true;
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "before1.xml");
    performAction();
    checkResultByFile(path + "after1.xml");
  }

  @Test
  public void testIndentInXmlInfiniteLoop() {
    configureByFile("/codeInsight/enterAction/indent/XmlInfiniteLoop.xml");
    performAction();
  }

  @Test
  public void testSCR19905() {
    configureByFile("/codeInsight/enterAction/SCR19905.xml");
    performAction();
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileText = ReadAction.computeBlocking(() -> myFixture.getFile().getText());
    assertEquals(myFixture.getEditor().getDocument().getText(), fileText);
  }

  @Test
  public void testHtml1() {
    configureByFile("/codeInsight/enterAction/html/1.html");
    performAction();
    checkResultByFile("/codeInsight/enterAction/html/1_after.html");
  }

  @Test
  public void testHtml2() {
    configureByFile("/codeInsight/enterAction/html/2.html");
    performAction();
    checkResultByFile("/codeInsight/enterAction/html/2_after.html");
  }

  @Test
  public void testBeginOfTag() {
    doTextTest("xml", """
                 <a>
                     <b/><caret>
                     <b/>
                 </a>""",
               """
                 <a>
                     <b/>
                     <caret>
                     <b/>
                 </a>""");
  }

  @Test
  public void testAssertionInXml() {
    doTextTest("xml", "<!--\n" + "TODO<caret>-->", """
      <!--
      TODO
      <caret>-->""");
  }

  @Test
  public void testEnterAtTheEndOfFile() {
    doTextTest("xml", "<a>\n" + "    <b><caret>", """
      <a>
          <b>
              <caret>""");
  }

  @Test
  public void testEnterAfterEmptyTag() {
    doTextTest("html",
               """
                 <form action="dsd">
                         <input type="text">
                         <input type="password">
                         <input type="submit"><caret></form>""",
               """
                 <form action="dsd">
                         <input type="text">
                         <input type="password">
                         <input type="submit">
                 </form>""");
  }

  @Test
  public void testWeb6656() {
    doTextTest(
      "html",

      "<!doctype html><caret>",

      "<!doctype html>\n" +
      "<caret>"
    );
  }
}
