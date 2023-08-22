// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

public class HtmlEmmetUpdateTagTest extends EmmetAbbreviationTestCase {
  public void testRemoveClass() {
    emmetUpdateTag("<span class=\"c1 c2\" title=\"hello\"><caret></span>", ".-c2", "<span class=\"c1\" title=\"hello\"></span>");
  }
  
  public void testAddClassAndModifyAttributes() {
    emmetUpdateTag("<span class=\"c1\" title=\"hello\"><caret></span>", ".+c3[-title a=b]", "<span class=\"c1 c3\" a=\"b\"></span>");
  }
  
  public void testChangeTagName() {
    emmetUpdateTag("<span class=\"c1 c3\" a=\"b\"><caret></span>", "div.+c3[-a]", "<div class=\"c1 c3\"></div>");
  }
  
  public void testAddClassWithIterationPlaceholder() {
    emmetUpdateTag("<div class=\"c1 c3\"><caret></div>", ".c$$$", "<div class=\"c000\"></div>");
  }

  @Override
  protected String getExtension() {
    return "html";
  }
}
