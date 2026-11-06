// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LoremAbbreviationTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");

  @Test
  public void testExpandLipsum() {
    emmet.get().expand("lipsum");
    assertWordCount(getFileText(), 30);
  }

  @Test
  public void testExpandLoremWithWordCount() {
    emmet.get().expand("lorem10");
    assertWordCount(getFileText(), 10);
  }

  @Test
  public void testLoremAsText() {
    emmet.get().expand("ul>li*3>lipsum4");
    ReadAction.runBlocking(() -> {
      XmlTag ul = PsiTreeUtil.findChildOfType(emmet.get().getFile(), XmlTag.class);
      assertNotNull(ul);
      XmlTag[] lis = ul.findSubTags("li");
      assertEquals(3, lis.length);
      assertEquals("Lorem ipsum dolor sit.", lis[0].getValue().getText());
    });
  }

  @Test
  public void testLoremWithImplicitTagName() {
    emmet.get().expand("ul>lipsum2*5");
    ReadAction.runBlocking(() -> {
      XmlTag ul = PsiTreeUtil.findChildOfType(emmet.get().getFile(), XmlTag.class);
      assertNotNull(ul);
      XmlTag[] lis = ul.findSubTags("li");
      assertEquals(5, lis.length);
      assertEquals("Lorem ipsum.", lis[0].getValue().getText());
    });
  }

  @Test
  public void testLoremWithImplicitTagName2() {
    emmet.get().expand("ul>lipsum2#ident");
    ReadAction.runBlocking(() -> {
      XmlTag ul = PsiTreeUtil.findChildOfType(emmet.get().getFile(), XmlTag.class);
      assertNotNull(ul);
      XmlTag[] lis = ul.findSubTags("li");
      assertEquals(1, lis.length);
      assertEquals("Lorem ipsum.", lis[0].getValue().getText());
      assertEquals("ident", lis[0].getAttributeValue("id"));
    });
  }

  @Test
  public void testLoremWithoutImplicitTagName() {
    emmet.get().expand("ul>lorem2");
    assertEquals("<ul>Lorem ipsum.</ul>", getFileText());
  }

  private String getFileText() {
    return ReadAction.computeBlocking(() -> emmet.get().getFile().getText());
  }

  private static void assertWordCount(String text, int expectedCount) {
    assertEquals(expectedCount, StringUtil.getWordsIn(text).size());
  }
}
