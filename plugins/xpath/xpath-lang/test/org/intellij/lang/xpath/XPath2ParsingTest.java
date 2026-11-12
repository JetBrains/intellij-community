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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@TestApplication
public class XPath2ParsingTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath2/parsing"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
  }

  @Test
  public void testComments() {
    doXPathHighlighting();
  }

  @Test
  public void testNodeComp() {
    doXPathHighlighting();
  }

  @Test
  public void testNewOps() {
    doXPathHighlighting();
  }

  @Test
  public void testSequence() {
    doXPathHighlighting();
  }

  @Test
  public void testEmptySequence() {
    doXPathHighlighting();
  }

  @Test
  public void testRange() {
    doXPathHighlighting();
  }

  @Test
  public void testIfThenElse() {
    doXPathHighlighting();
  }

  @Test
  public void testIfThenElseMissing() {
    doXPathHighlighting();
  }

  @Test
  public void testForLoop() {
    doXPathHighlighting();
  }

  @Test
  public void testSomeEvery() {
    doXPathHighlighting();
  }

  @Test
  public void testTypeExpressions() {
    doXPathHighlighting();
  }

  @Test
  public void testDoubleLiterals() {
    doXPathHighlighting();
  }

  @Test
  public void testWildcardNamespace() {
    doXPathHighlighting();
  }

  @Test
  public void testInvalidWildcard() {
    doXPathHighlighting();
  }

  @Test
  public void testMissingInstanceOfType() {
    doXPathHighlighting();
  }

  @Test
  public void testMissingSatisfiesExpression() {
    doXPathHighlighting();
  }

  @Test
  public void testValidTypeOccurrenceIndicators() {
    doXPathHighlighting();
  }

  @Test
  public void testInvalidTypeOccurrenceIndicator1() {
    doXPathHighlighting();
  }

  @Test
  public void testInvalidTypeOccurrenceIndicator2() {
    doXPathHighlighting();
  }

  @Test
  public void testNonKeywords() {
    doXPathHighlighting();
  }

  // IDEA-70681
  @Test
  public void testCastWithMultiplication() {
    doXPathHighlighting();
  }

  // IDEA-70688
  @Test
  public void testKindTestWithStar() {
    doXPathHighlighting();
  }

  @Test
  public void testUnionSubExpression() {
    doXPathHighlighting();
  }

  // IDEA-67413
  private void doXPathHighlighting() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = testFileName;
    myFixture.testHighlighting(false, false, false, name + ".xpath2");
  }
}
