// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import org.jetbrains.annotations.NotNull;

public class XmlCommentByLineTest extends CommentByLineTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testXml1() {
    doTest();
  }

  public void testXml2() {
    doTest();
  }

  public void testXml3() {
    doTest();
  }

  public void testXml4() {
    getLanguageSettings(XMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testXml5() {
    getLanguageSettings(XMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testXml6() {
    doTest();
  }

  public void testXml7() {
    doTest();
  }

  public void testXml8() {
    doTest();
  }
  public void testXml9() {
    doTest();
  }

  public void testXml10() { // this is a feature
    doTest();
  }

  public void testXml11() {
    doTest();
  }

  public void testXml_DoubleDash() {
    doInvertedTest(1);
  }

  public void testXml_SingleDash() {
    doTest();
  }

  public void testXml_SingleDashWithSpace() {
    doTest();
  }

  public void testXml_OtherLine() {
    doTest();
  }

  public void testHtml1() {
    doTest();
  }

  public void testHtml2() {
    doTest();
  }

  public void testHtml3() {
    doTest();
  }

  public void testHtml4() {
    getLanguageSettings(HTMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testHtml5() {
    getLanguageSettings(HTMLLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testHtml6() {
    doTest();
  }

  public void testHtml7() {
    doTest();
  }

  public void testHtml8() {
    doTest();
  }

  public void testHtml9() {
    doTest();
  }

  public void testHtml10() {
    doTest();
  }
}
