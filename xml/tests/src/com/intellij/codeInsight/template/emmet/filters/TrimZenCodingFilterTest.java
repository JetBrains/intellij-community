// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

public class TrimZenCodingFilterTest extends EmmetFilterTestCase {
  private final ZenCodingFilter myFilter = new TrimZenCodingFilter();

  public void testTrimFilter1() {
    expandAndCheck("{1. test}|t", "test");
  }

  public void testTrimFilter2() {
    expandAndCheck("{ 1 test}|t", "test");
  }

  public void testTrimFilter3() {
    expandAndCheck("{ * test}|t", "test");
  }

  public void testTrimFilterWithWrapping() {
    emmetWrap("""
                1. list item one
                2. list item two
                3. list item three""", "div*", """
                <div>1. list item one</div>
                <div>2. list item two</div>
                <div>3. list item three</div>""");

    emmetWrap("""
                1. list item one
                2. list item two
                3. list item three""", "div*|t", """
                <div>list item one</div>
                <div>list item two</div>
                <div>list item three</div>""");
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
