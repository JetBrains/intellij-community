// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class XmlCommentByBlockTest extends LightPlatformCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testXml1() {
    configureByFile("/codeInsight/commentByBlock/xml/before1.xml");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/xml/after1.xml");
  }

  public void testXml2() {
    configureByFile("/codeInsight/commentByBlock/xml/before2.xml");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/xml/after2.xml");
  }

  public void test3() {
    doTest();
  }

  public void testDoubleDash() {
    doTest();
  }

  public void testSingleDash() {
    doTest(false);
  }

  public void testSelfClosing() {
    doTest();
  }

  private void doTest() {
    doTest(false);
    doTest(true);
  }

  private void doTest(boolean invert) {
    String test = getTestName(false);
    String before = invert ? "after" : "before";
    configureByFile("/codeInsight/commentByBlock/xml/" + before + test + ".xml");
    performAction();
    String after = invert ? "before" : "after";
    checkResultByFile("/codeInsight/commentByBlock/xml/" + after + test + ".xml");
  }

  public void testHtml1() {
    configureByFile("/codeInsight/commentByBlock/html/before1.html");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/html/after1.html");
  }

  public void testHtml2() {
    configureByFile("/codeInsight/commentByBlock/html/before2.html");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/html/after2.html");
  }

  public void testNestedXml() {
    configureByFile("/codeInsight/commentByBlock/xml/before4.xml");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/xml/after4.xml");
  }

  public void testNestedHtml() {
    configureByFile("/codeInsight/commentByBlock/html/before3.html");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/html/after3.html");
  }

  public void testRightUncommentBoundsXml() {
    configureByFile("/codeInsight/commentByBlock/xml/before5.xml");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/xml/after5.xml");
  }


  public void testIdeaDev25498_1() {
    configureByFile("/codeInsight/commentByBlock/xml/beforeIdeaDev25498_1.xml");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/xml/afterIdeaDev25498_1.xml");
  }

  public void testIdeaDev25498_2() {
    configureByFile("/codeInsight/commentByBlock/xml/beforeIdeaDev25498_2.xml");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/xml/afterIdeaDev25498_2.xml");
  }

  private void performAction() {
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_COMMENT_BLOCK);
  }
}
