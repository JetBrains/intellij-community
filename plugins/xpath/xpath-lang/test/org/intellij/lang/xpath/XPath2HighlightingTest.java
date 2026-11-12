/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.intellij.lang.xpath.validation.inspections.RedundantTypeConversion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@TestApplication
public class XPath2HighlightingTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath2/highlighting"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    codeInsightFixture.get().enableInspections(RedundantTypeConversion.class);
  }

  @Test
  public void testIfInsideArgumentList() {
    doXPathHighlighting();
  }

  @Test
  public void testConsecutiveComparison() {
    doXPathHighlighting();
  }

  @Test
  public void testNumberFollowedByToken() {
    doXPathHighlighting();
  }

  @Test
  public void testNumberFollowedByToken2() {
    doXPathHighlighting();
  }

  @Test
  public void testMalformedStringLiteral() {
    doXPathHighlighting();
  }

  @Test
  public void testQuotedStringLiteral() {
    doXPathHighlighting();
  }

  @Test
  public void testEmptyStringLiteral() {
    doXPathHighlighting();
  }

  @Test
  public void testNodeKindTest() {
    doXPathHighlighting();
  }

  @Test
  public void testIncorrectNodeKindTests() {
    doXPathHighlighting();
  }

  @Test
  public void testValidOperations() {
    doXPathHighlighting();
  }

  @Test
  public void testValidOperations2() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testPlusOperatorNotApplicable() {
    doXPathHighlighting();
  }

  @Test
  public void testToOperatorNotApplicable() {
    doXPathHighlighting();
  }

  @Test
  public void testIntersectOperatorNotApplicable() {
    doXPathHighlighting();
  }

  @Test
  public void testUnionOperatorNotApplicable() {
    doXPathHighlighting();
  }

  @Test
  public void testInvalidNodeTypePredicate() {
    doXPathHighlighting();
  }

  private void doXPathHighlighting() {
    codeInsightFixture.get().testHighlighting(true, false, false, testFileName + ".xpath2");
  }
}