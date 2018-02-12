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

public class XPath2TypeTest extends XPath2HighlightingTestBase {

  public void testQNameToQName() {
    doXPathHighlighting();
  }

  public void testQNameToBoolean() {
    doXPathHighlighting();
  }

  public void testStringToQName() {
    doXPathHighlighting();
  }

  public void testStringToBoolean() {
    doXPathHighlighting();
  }

  public void testUriToBoolean() {
    doXPathHighlighting();
  }

  public void testUriToStringSequence() {
    doXPathHighlighting();
  }

  public void testNumberToNode() {
    doXPathHighlighting();
  }

  public void testNodeToNode() {
    doXPathHighlighting();
  }

  public void testNodeToDouble() {
    doXPathHighlighting();
  }

  public void testAnyToString() {
    doXPathHighlighting();
  }

  public void testStringToAny() {
    doXPathHighlighting();
  }

  public void testIntegerToString() {
    doXPathHighlighting();
  }

  public void testIntSeqToAnySeq() {
    doXPathHighlighting();
  }

  public void testIntSeqToStringSeq() {
    doXPathHighlighting();
  }

  public void testDynamicContext1() {
    doXPathHighlighting();
  }

  public void testDynamicContext2() {
    doXPathHighlighting();
  }

  public void testDynamicContext3() {
    doXPathHighlighting();
  }

  public void testStringFunctionOnPath() {
    doXPathHighlighting();
  }

  public void testInvalidPath() {
    doXPathHighlighting();
  }

  public void testToNumericIDEA67335() {
    doXPathHighlighting();
  }

  public void testBooleanComparisonIDEA67348() {
    doXPathHighlighting();
  }

  public void testDurationPlusDurationPlusDateIDEA69517() {
    doXPathHighlighting();
  }

  public void testNumberPlusString() {
    doXPathHighlighting();
  }

  public void testUnaryExpressionType() {
    doXPathHighlighting();
  }

  public void testDatePlusDuration() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testDatePlusDate() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testDateMinusDate() {
    doXPathHighlighting();
  }

  public void testTimeMinusTime() {
    doXPathHighlighting();
  }

  public void testDateTimeMinusDateTime() {
    doXPathHighlighting();
  }

  public void testTimeMinusDate() {
    doXPathHighlighting();
  }

  public void testDateMinusDuration() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testDateMinusDuration2() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testDurationMinusDate() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testNumericDivDuration() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testNumericIDivDuration() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testDurationDivNumeric() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testDurationIDivNumeric() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testNumericMultString() {
    doXPathHighlighting();
  }

  public void testQuantifiedExprCondition() {
    doXPathHighlighting();
  }

  public void testRedundantTypeConversion() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  @Override
  protected String getSubPath() {
    return "xpath2/highlighting/types";
  }
}
