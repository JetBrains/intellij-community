// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class HtmlEmmetUpdateTagTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");

  @Test
  public void testRemoveClass() {
    emmet.get().emmetUpdateTag("<span class=\"c1 c2\" title=\"hello\"><caret></span>", ".-c2", "<span class=\"c1\" title=\"hello\"></span>");
  }
  
  @Test
  public void testAddClassAndModifyAttributes() {
    emmet.get().emmetUpdateTag("<span class=\"c1\" title=\"hello\"><caret></span>", ".+c3[-title a=b]", "<span class=\"c1 c3\" a=\"b\"></span>");
  }
  
  @Test
  public void testChangeTagName() {
    emmet.get().emmetUpdateTag("<span class=\"c1 c3\" a=\"b\"><caret></span>", "div.+c3[-a]", "<div class=\"c1 c3\"></div>");
  }
  
  @Test
  public void testAddClassWithIterationPlaceholder() {
    emmet.get().emmetUpdateTag("<div class=\"c1 c3\"><caret></div>", ".c$$$", "<div class=\"c000\"></div>");
  }
}
