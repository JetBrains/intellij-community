// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

public class CommentZenCodingFilterTest extends EmmetFilterTestCase {
  private final ZenCodingFilter myFilter = new CommentZenCodingFilter();

  public void testCommentTagWithId() {
    String text = "<div id=\"idName\"></div>";
    doFilterText(text, text + "\n<!-- /#idName -->");
  }

  public void testCommentTagWithClass() {
    String text = "<div class=\"clName\"></div>";
    doFilterText(text, text + "\n<!-- /.clName -->");
  }

  public void testCommentTagWithIdAndClass() {
    String text = "<div id=\"idName\" class=\"clName\"></div>";
    doFilterText(text, text + "\n<!-- /#idName.clName -->");
  }

  public void testDoNotCommentTagWithoutClassAndId() {
    String text = "<div></div>";
    doFilterText(text, text);
  }

  public void testInvokeByPrefix() {
    expand("div.className|c");


    checkResultByText("<div class=\"className\"></div>\n<!-- /.className -->");
  }

  @Override
  protected ZenCodingFilter getFilter() {
    return myFilter;
  }

  @Override
  protected String getExtension() {
    return "html";
  }
}
