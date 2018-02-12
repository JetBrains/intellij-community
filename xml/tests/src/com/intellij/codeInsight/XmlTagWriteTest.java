/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;

/**
 * @author lesya, dsl
 */
public class XmlTagWriteTest extends LightCodeInsightTestCase{
  public void test1() throws IncorrectOperationException {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(getProject());
    final XmlTag xmlTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<tag1/>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      xmlTag.add(xmlTag.createChildTag("tag2", XmlUtil.EMPTY_URI, null, false));
    });


    assertEquals("<tag1><tag2/></tag1>", xmlTag.getText());
    XmlTag createdFromText = elementFactory.createTagFromText(xmlTag.getText());
    assertEquals("tag1", createdFromText.getName());
    assertEquals(1, createdFromText.getSubTags().length);
    assertEquals("tag2", createdFromText.getSubTags()[0].getName());
  }

  public void test2() throws IncorrectOperationException {
    final XmlTag xmlTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<tag1></tag1>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      xmlTag.add(xmlTag.createChildTag("tag2", XmlUtil.EMPTY_URI, null, false));
    });


    assertEquals("<tag1><tag2/></tag1>", xmlTag.getText());
    XmlTag createdFromText = XmlElementFactory.getInstance(getProject()).createTagFromText(xmlTag.getText());
    assertEquals("tag1", createdFromText.getName());
    assertEquals(1, createdFromText.getSubTags().length);
    assertEquals("tag2", createdFromText.getSubTags()[0].getName());
  }

  public void test3() {
    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<b>\n0123456</b>");
    final XmlText text = (XmlText) tag.getValue().getChildren()[0];
    String textS = text.getText();
    ApplicationManager.getApplication().runWriteAction(() -> text.insertText("lala", 2));

    XmlText text2 = (XmlText)tag.getValue().getChildren()[0];
    assertEquals(textS.substring(0, 2) + "lala" + textS.substring(2), text2.getText());
  }
}
