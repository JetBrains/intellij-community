/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.validation.inspections.CheckNodeTest;
import org.intellij.lang.xpath.validation.inspections.HardwiredNamespacePrefix;
import org.intellij.lang.xpath.validation.inspections.ImplicitTypeConversion;
import org.intellij.lang.xpath.validation.inspections.IndexZeroPredicate;
import org.intellij.lang.xpath.validation.inspections.RedundantTypeConversion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@TestApplication
public class XPathHighlightingTest {
    private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
    private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
    private String testFileName;

    @BeforeEach
    void setUp(TestInfo testInfo) {
      CodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.setTestDataPath(XPathTestUtil.getTestDataPath("xpath/highlighting"));
      testFileName = XPathTestUtil.getTestFileName(testInfo);
      //noinspection unchecked
      myFixture.enableInspections(CheckNodeTest.class,
                                  ImplicitTypeConversion.class,
                                  RedundantTypeConversion.class,
                                  IndexZeroPredicate.class,
                                  HardwiredNamespacePrefix.class);
    }

    @Test
    public void testPathTypeMismatch() {
        doXPathHighlighting();
    }

    @Test
    public void testUnknownFunction() {
        doXPathHighlighting();
    }

    @Test
    public void testMissingArgument() {
        doXPathHighlighting();
    }

    @Test
    public void testInvalidArgument() {
        doXPathHighlighting();
    }

    @Test
    public void testIndexZero() {
        doXPathHighlighting();
    }

    @Test
    public void testSillyStep() {
        doXPathHighlighting();
    }

    @Test
    public void testNonSillyStepIDEADEV33539() {
        doXPathHighlighting();
    }

    @Test
    public void testHardwiredPrefix() {
        doXPathHighlighting();
    }

    @Test
    public void testNumberFollowedByToken() {
      doXPathHighlighting();
    }

    @Test
    public void testScientificNotationNumber() {
      doXPathHighlighting();
    }

    @Test
    public void testMalformedStringLiteral() {
      doXPathHighlighting();
    }

    @Test
    public void testMalformedStringLiteral2() {
      doXPathHighlighting();
    }

    @Test
    public void testMalformedEmptyStringLiteral() {
      doXPathHighlighting();
    }

    @Test
    public void testQuotedStringLiteral() {
      doXPathHighlighting();
    }

    // IDEA-67413
    @Test
    public void testUnionSubExpression() {
      doXPathHighlighting();
    }

    // IDEA-102422
    @Test
    public void testPrefixedNameAnd() {
      doXPathHighlighting();
    }

    private void doXPathHighlighting(String... moreFiles) {
        CodeInsightTestFixture myFixture = codeInsightFixture.get();
        final String name = testFileName;
        myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xpath"));
    }
}
