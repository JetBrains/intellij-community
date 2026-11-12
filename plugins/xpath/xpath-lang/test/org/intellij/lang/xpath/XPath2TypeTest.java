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
public class XPath2TypeTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath2/highlighting/types"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    codeInsightFixture.get().enableInspections(RedundantTypeConversion.class);
  }

  @Test
  public void testQNameToQName() {
    doXPathHighlighting();
  }

  @Test
  public void testQNameToBoolean() {
    doXPathHighlighting();
  }

  @Test
  public void testStringToQName() {
    doXPathHighlighting();
  }

  @Test
  public void testStringToBoolean() {
    doXPathHighlighting();
  }

  @Test
  public void testUriToBoolean() {
    doXPathHighlighting();
  }

  @Test
  public void testUriToStringSequence() {
    doXPathHighlighting();
  }

  @Test
  public void testNumberToNode() {
    doXPathHighlighting();
  }

  @Test
  public void testNodeToNode() {
    doXPathHighlighting();
  }

  @Test
  public void testNodeToDouble() {
    doXPathHighlighting();
  }

  @Test
  public void testAnyToString() {
    doXPathHighlighting();
  }

  @Test
  public void testStringToAny() {
    doXPathHighlighting();
  }

  @Test
  public void testIntegerToString() {
    doXPathHighlighting();
  }

  @Test
  public void testIntSeqToAnySeq() {
    doXPathHighlighting();
  }

  @Test
  public void testIntSeqToStringSeq() {
    doXPathHighlighting();
  }

  @Test
  public void testDynamicContext1() {
    doXPathHighlighting();
  }

  @Test
  public void testDynamicContext2() {
    doXPathHighlighting();
  }

  @Test
  public void testDynamicContext3() {
    doXPathHighlighting();
  }

  @Test
  public void testStringFunctionOnPath() {
    doXPathHighlighting();
  }

  @Test
  public void testInvalidPath() {
    doXPathHighlighting();
  }

  @Test
  public void testToNumericIDEA67335() {
    doXPathHighlighting();
  }

  @Test
  public void testBooleanComparisonIDEA67348() {
    doXPathHighlighting();
  }

  @Test
  public void testDurationPlusDurationPlusDateIDEA69517() {
    doXPathHighlighting();
  }

  @Test
  public void testNumberPlusString() {
    doXPathHighlighting();
  }

  @Test
  public void testUnaryExpressionType() {
    doXPathHighlighting();
  }

  @Test
  public void testDatePlusDuration() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testDatePlusDate() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testDateMinusDate() {
    doXPathHighlighting();
  }

  @Test
  public void testTimeMinusTime() {
    doXPathHighlighting();
  }

  @Test
  public void testDateTimeMinusDateTime() {
    doXPathHighlighting();
  }

  @Test
  public void testTimeMinusDate() {
    doXPathHighlighting();
  }

  @Test
  public void testDateMinusDuration() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testDateMinusDuration2() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testDurationMinusDate() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testNumericDivDuration() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testNumericIDivDuration() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testDurationDivNumeric() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testDurationIDivNumeric() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Test
  public void testNumericMultString() {
    doXPathHighlighting();
  }

  @Test
  public void testQuantifiedExprCondition() {
    doXPathHighlighting();
  }

  @Test
  public void testRedundantTypeConversion() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  private void doXPathHighlighting() {
    codeInsightFixture.get().testHighlighting(true, false, false, testFileName + ".xpath2");
  }
}