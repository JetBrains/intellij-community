// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class EscapeZenCodingFilterTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");
  private final ZenCodingFilter myFilter = new EscapeZenCodingFilter();

  @Test
  public void testEscape() {
    String text = "<div id=\"&idName\"></div>";
    emmet.get().doFilterText(text, "&lt;div id=\"&amp;idName\"&gt;&lt;/div&gt;", myFilter);
  }

  @Test
  public void testDoubleEscape() {
    String text = "<div id=\"&idName\"></div>";
    String expectedText1 = "&lt;div id=\"&amp;idName\"&gt;&lt;/div&gt;";
    emmet.get().doFilterText(text, expectedText1, myFilter);
    emmet.get().doFilterText(expectedText1, "&amp;lt;div id=\"&amp;amp;idName\"&amp;gt;&amp;lt;/div&amp;gt;", myFilter);
  }

  @Test
  public void testInvokeByPrefix() {
    emmet.get().expandAndCheck("a>b|e", "&lt;a href=\"\"&gt;&lt;b&gt;&lt;/b&gt;&lt;/a&gt;");
  }

  @Test
  public void testInvokeByDoublePrefix() {
    emmet.get().expandAndCheck("a>b|e|e", "&amp;lt;a href=\"\"&amp;gt;&amp;lt;b&amp;gt;&amp;lt;/b&amp;gt;&amp;lt;/a&amp;gt;");
  }
}
