// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class CommentZenCodingFilterTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");
  private final ZenCodingFilter myFilter = new CommentZenCodingFilter();

  @Test
  public void testCommentTagWithId() {
    String text = "<div id=\"idName\"></div>";
    emmet.get().doFilterText(text, text + "\n<!-- /#idName -->", myFilter);
  }

  @Test
  public void testCommentTagWithClass() {
    String text = "<div class=\"clName\"></div>";
    emmet.get().doFilterText(text, text + "\n<!-- /.clName -->", myFilter);
  }

  @Test
  public void testCommentTagWithIdAndClass() {
    String text = "<div id=\"idName\" class=\"clName\"></div>";
    emmet.get().doFilterText(text, text + "\n<!-- /#idName.clName -->", myFilter);
  }

  @Test
  public void testDoNotCommentTagWithoutClassAndId() {
    String text = "<div></div>";
    emmet.get().doFilterText(text, text, myFilter);
  }

  @Test
  public void testInvokeByPrefix() {
    emmet.get().expand("div.className|c");


    emmet.get().getFixture().checkResult("<div class=\"className\"></div>\n<!-- /.className -->");
  }
}
