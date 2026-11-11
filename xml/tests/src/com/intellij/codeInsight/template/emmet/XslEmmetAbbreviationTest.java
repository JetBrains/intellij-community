// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.google.common.base.Joiner;
import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightProjectFixture;

@TestApplication
public class XslEmmetAbbreviationTest {
  private static final TestFixture<Project> projectFixture = codeInsightProjectFixture();
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture(projectFixture, "xsl");

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void abbreviation(EmmetAbbreviationTestCase testCase) {
    emmet.get().checkAbbreviation(testCase);
  }

  static List<Named<EmmetAbbreviationTestCase>> cases() throws IOException {
    return new Cases().toNamedCases();
  }

  private static final class Cases extends EmmetAbbreviationTestCases {
    Cases() throws IOException {
      addXslAbbreviationTests();
      addXslTests();
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
      super.addTestWithName("tmatch", "tmatch #2", "<xsl:template match=\"\" mode=\"\"></xsl:template>", null, "xsl");
      super.addTestWithName("choose", "choose #2", "<xsl:choose><xsl:when test=\"\"></xsl:when><xsl:otherwise></xsl:otherwise></xsl:choose>", null, "xsl");
      addTest("xsl:variable>div+p", "<xsl:variable><div></div><p></p></xsl:variable>", "xsl");
      addTest("var>div+p", "<xsl:variable name=\"\"><div></div><p></p></xsl:variable>", "xsl");
      super.addTestWithName("ap", "ap #2", "<xsl:apply-templates select=\"\" mode=\"\" />", null, "xsl");
      addTest("ap>wp*2",
              "<xsl:apply-templates select=\"\" mode=\"\"><xsl:with-param name=\"\" select=\"\" /><xsl:with-param name=\"\" select=\"\" /></xsl:apply-templates>",
              "xsl");
    }

    @NotNull
    private String getTestDataPath() {
      return Joiner.on(File.separatorChar).join(XmlTestUtil.getXmlTestDataPath(), "codeInsight", "template", "emmet", "abbreviation");
    }
  }
}
