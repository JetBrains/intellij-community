// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

public class EscapeZenCodingFilterTest extends EmmetFilterTestCase {
  private final ZenCodingFilter myFilter = new EscapeZenCodingFilter();

  public void testEscape() {
    String text = "<div id=\"&idName\"></div>";
    doFilterText(text, "&lt;div id=\"&amp;idName\"&gt;&lt;/div&gt;");
  }

  public void testDoubleEscape() {
    String text = "<div id=\"&idName\"></div>";
    String expectedText1 = "&lt;div id=\"&amp;idName\"&gt;&lt;/div&gt;";
    doFilterText(text, expectedText1);
    doFilterText(expectedText1, "&amp;lt;div id=\"&amp;amp;idName\"&amp;gt;&amp;lt;/div&amp;gt;");
  }

  public void testInvokeByPrefix() {
    expandAndCheck("a>b|e", "&lt;a href=\"\"&gt;&lt;b&gt;&lt;/b&gt;&lt;/a&gt;");
  }

  public void testInvokeByDoublePrefix() {
    expandAndCheck("a>b|e|e", "&amp;lt;a href=\"\"&amp;gt;&amp;lt;b&amp;gt;&amp;lt;/b&amp;gt;&amp;lt;/a&amp;gt;");
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
