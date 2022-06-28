// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class XmlSurroundWithTest extends MarkupSurroundTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testHtml() {
    doSurroundWithTagTest("html");
  }

  public void testHtml2() {
    doSurroundWithTagTest("html");
  }

  public void testHtml3() {
    doSurroundWithTagTest("html");
  }

  public void testXml() {
    doSurroundWithTagTest("xml");
  }

  public void testXml2() {
    doSurroundWithCDataTest("xml");
  }

  public void testXml3() {
    doSurroundWithTagTest("xml");
  }

  public void testMultiCaretSurroundWithCData() {
    doSurroundWithCDataTest("xml");
  }

  public void testSurroundWithTagFirstElement() {
    configureByFile(BASE_PATH + "tag/Xml.xml");
    AnAction firstAction = SurroundWithTemplateHandler.createActionGroup(getEditor(), getFile(), new HashSet<>()).get(0);
    assertInstanceOf(firstAction, InvokeTemplateAction.class);
    TemplateImpl template = ((InvokeTemplateAction)firstAction).getTemplate();
    assertEquals("T", template.getKey());
  }
}
