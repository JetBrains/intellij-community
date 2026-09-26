// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import org.jetbrains.annotations.NotNull;

public class XmlSelectWordTest extends SelectWordTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testHtml1() {
    doTest("html");
  }

  public void testHtml2() {
    doTest("html");
  }

  public void testHtml3() {
    doTest("html");
  }

  public void testHtml4() {
    doTest("html");
  }

  public void testHtml5() {
    doTest("html");
  }

  public void testHtml6() {
    doTest("html");
  }
  public void testHtml7() {
    doTest("html");
  }

  public void testHtml8() {
    doTest("html");
  }

  public void testHtml9() {
    doTest("html");
  }

  public void testHtml10() {
    doTest("html");
  }

  public void testHtml11() {
    doTest("html");
  }

  public void testHtml12() {
    doTest("html");
  }

  public void testHtml13() {
    doTest("html");
  }

  public void testHtml14() {
    doTest("html");
  }

  public void testSelectPathInStringLiteral() {
    doTest("html");
  }

  public void testSelectPathBeforeStringLiteral() {
    doTest("html");
  }

  public void testXml1() {
    doTest("xml");
  }
  public void testXml11() {
    doTest("xml");
  }

  public void testXml2() {
    doTest("xml");
  }

  public void testDtd() {
    doTest("dtd");
  }
  public void testDtd1() {
    doTest("dtd");
  }

  public void testXml3() {
    doTest("xml");
  }

  public void testXmlStartLine() {
    doTest("xml");
  }

  public void testXmlCommentAboveTag() {
    doTest("xml");
  }
}
