// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class XmlEnterActionTest extends AbstractEnterActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testInXml1() {
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "state0.xml");
    performAction();
    checkResultByFile(path + "state1.xml");
  }

  public void testInXml2() {
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "state2.xml");
    performAction();
    PsiDocumentManager.getInstance(getProject()).commitDocument(getEditor().getDocument());
    assertEquals(getFile().getText(), getEditor().getDocument().getText());
    //checkResultByFile(path + "state3.xml");
  }

  public void testInXml3() {
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "state4.xml");
    performAction();
    PsiDocumentManager.getInstance(getProject()).commitDocument(getEditor().getDocument());
    assertEquals(getFile().getText(), getEditor().getDocument().getText());
    //    checkResultByFile(path + "state5.xml");
  }

  public void testInXml4() {
    getCodeStyleSettings().getCustomSettings(XmlCodeStyleSettings.class).XML_ALIGN_ATTRIBUTES = true;
    String path = "/codeInsight/enterAction/xml/";

    configureByFile(path + "before1.xml");
    performAction();
    checkResultByFile(path + "after1.xml");
  }

  public void testIndentInXmlInfiniteLoop() {
    configureByFile("/codeInsight/enterAction/indent/XmlInfiniteLoop.xml");
    performAction();
  }

  public void testSCR19905() {
    configureByFile("/codeInsight/enterAction/SCR19905.xml");
    performAction();
    assertEquals(getEditor().getDocument().getText(), getFile().getText());
  }

  public void testHtml1() {
    configureByFile("/codeInsight/enterAction/html/1.html");
    performAction();
    checkResultByFile(null, "/codeInsight/enterAction/html/1_after.html", false);
  }

  public void testHtml2() {
    configureByFile("/codeInsight/enterAction/html/2.html");
    performAction();
    checkResultByFile(null, "/codeInsight/enterAction/html/2_after.html", false);
  }

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

  public void testAssertionInXml() {
    doTextTest("xml", "<!--\n" + "TODO<caret>-->", """
      <!--
      TODO
      <caret>-->""");
  }

  public void testEnterAtTheEndOfFile() {
    doTextTest("xml", "<a>\n" + "    <b><caret>", """
      <a>
          <b>
              <caret>""");
  }

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

  public void testWeb6656() {
    doTextTest(
      "html",

      "<!doctype html><caret>",

      "<!doctype html>\n" +
      "<caret>"
    );
  }
}
