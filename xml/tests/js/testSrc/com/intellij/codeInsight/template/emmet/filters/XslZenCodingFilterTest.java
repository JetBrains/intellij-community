// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class XslZenCodingFilterTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("xsl"); //todo make xsl template available in xml with |xsl filter

  @Test
  public void testXsl1() {
    emmet.get().expand("vare|xsl");
    emmet.get().getFixture().checkResult("<xsl:variable name=\"\" select=\"\"/>");
  }

  @Test
  public void testXsl2() {
    emmet.get().expand("vare>p|xsl");
    emmet.get().getFixture().checkResult("<xsl:variable name=\"\">\n    <p></p>\n</xsl:variable>");
  }
}
