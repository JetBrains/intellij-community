// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;

public class LoremAbbreviationTest extends EmmetAbbreviationTestCase {

  public void testExpandLipsum() {
    expand("lipsum");
    assertWordCount(getFile().getText(), 30);
  }

  public void testExpandLoremWithWordCount() {
    expand("lorem10");
    assertWordCount(getFile().getText(), 10);
  }

  public void testLoremAsText() {
    expand("ul>li*3>lipsum4");
    XmlTag ul = PsiTreeUtil.findChildOfType(getFile(), XmlTag.class);
    assertNotNull(ul);
    XmlTag[] lis = ul.findSubTags("li");
    assertEquals(3, lis.length);
    assertEquals("Lorem ipsum dolor sit.", lis[0].getValue().getText());
  }

  public void testLoremWithImplicitTagName() {
    expand("ul>lipsum2*5");
    XmlTag ul = PsiTreeUtil.findChildOfType(getFile(), XmlTag.class);
    assertNotNull(ul);
    XmlTag[] lis = ul.findSubTags("li");
    assertEquals(5, lis.length);
    assertEquals("Lorem ipsum.", lis[0].getValue().getText());
  }

  public void testLoremWithImplicitTagName2() {
    expand("ul>lipsum2#ident");
    XmlTag ul = PsiTreeUtil.findChildOfType(getFile(), XmlTag.class);
    assertNotNull(ul);
    XmlTag[] lis = ul.findSubTags("li");
    assertEquals(1, lis.length);
    assertEquals("Lorem ipsum.", lis[0].getValue().getText());
    assertEquals("ident", lis[0].getAttributeValue("id"));
  }

  public void testLoremWithoutImplicitTagName() {
    expand("ul>lorem2");
    assertEquals("<ul>Lorem ipsum.</ul>", getFile().getText());
  }

  @Override
  protected String getExtension() {
    return "html";
  }

  private static void assertWordCount(String text, int expectedCount) {
    assertEquals(expectedCount, StringUtil.getWordsIn(text).size());
  }
}
