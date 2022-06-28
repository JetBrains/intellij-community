// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.google.common.base.Joiner;
import com.intellij.codeInsight.XmlTestUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class XslEmmetAbbreviationTest extends EmmetAbbreviationTestSuite {
  public XslEmmetAbbreviationTest() throws IOException {
    addXslAbbreviationTests();
    addXslTests();
  }

  public static Test suite() throws IOException {
    return new XslEmmetAbbreviationTest();
  }

  /**
   * Testing all abbreviations from https://github.com/emmetio/emmet/blob/master/snippets.json
   */
  public void addXslAbbreviationTests() throws IOException {
    addTestFromJson(getTestDataPath() + "/xsl.abbreviation.json", "xsl");
  }

  /**
   * Tests from https://github.com/emmetio/emmet/blob/master/javascript/unittest/tests/expandAbbreviation.js
   */
  public void addXslTests() {
    addTest("tmatch", "<xsl:template match=\"\" mode=\"\"></xsl:template>", "xsl");
    addTest("choose", "<xsl:choose><xsl:when test=\"\"></xsl:when><xsl:otherwise></xsl:otherwise></xsl:choose>", "xsl");
    addTest("xsl:variable>div+p", "<xsl:variable><div></div><p></p></xsl:variable>", "xsl");
    addTest("var>div+p", "<xsl:variable name=\"\"><div></div><p></p></xsl:variable>", "xsl");
    addTest("ap", "<xsl:apply-templates select=\"\" mode=\"\" />", "xsl");
    addTest("ap>wp*2",
            "<xsl:apply-templates select=\"\" mode=\"\"><xsl:with-param name=\"\" select=\"\" /><xsl:with-param name=\"\" select=\"\" /></xsl:apply-templates>",
            "xsl");
  }

  @NotNull
  protected String getTestDataPath() {
    return Joiner.on(File.separatorChar).join(XmlTestUtil.getXmlTestDataPath(), "codeInsight", "template", "emmet", "abbreviation");
  }
}
