// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

public class XslZenCodingFilterTest extends EmmetFilterTestCase {
  private final ZenCodingFilter myFilter = new XslZenCodingFilter();

  public void testXsl1() {
    expand("vare|xsl");
    checkResultByText("<xsl:variable name=\"\" select=\"\"/>");
  }

  public void testXsl2() {
    expand("vare>p|xsl");
    checkResultByText("<xsl:variable name=\"\">\n    <p></p>\n</xsl:variable>");
  }

  @Override
  protected ZenCodingFilter getFilter() {
    return myFilter;
  }

  @Override
  protected String getExtension() {
    return "xsl"; //todo make xsl template available in xml with |xsl filter
  }
}
