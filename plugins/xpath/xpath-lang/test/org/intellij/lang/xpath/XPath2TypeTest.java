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

import org.intellij.lang.xpath.context.ContextProvider;

public class XPath2TypeTest extends XPath2HighlightingTestBase {

  public void testQNameToQName() throws Throwable {
    doXPathHighlighting();
  }

  public void testQNameToBoolean() throws Throwable {
    doXPathHighlighting();
  }

  public void testStringToQName() throws Throwable {
    doXPathHighlighting();
  }

  public void testStringToBoolean() throws Throwable {
    doXPathHighlighting();
  }

  public void testUriToBoolean() throws Throwable {
    doXPathHighlighting();
  }

  public void testUriToStringSequence() throws Throwable {
    doXPathHighlighting();
  }

  public void testNumberToNode() throws Throwable {
    doXPathHighlighting();
  }

  public void testNodeToNode() throws Throwable {
    doXPathHighlighting();
  }

  public void testNodeToDouble() throws Throwable {
    doXPathHighlighting();
  }

  public void testAnyToString() throws Throwable {
    doXPathHighlighting();
  }

  public void testStringToAny() throws Throwable {
    doXPathHighlighting();
  }

  public void testIntegerToString() throws Throwable {
    doXPathHighlighting();
  }

  public void testIntSeqToAnySeq() throws Throwable {
    doXPathHighlighting();
  }

  public void testIntSeqToStringSeq() throws Throwable {
    doXPathHighlighting();
  }

  public void testDynamicContext1() throws Throwable {
    doXPathHighlighting();
  }

  public void testDynamicContext2() throws Throwable {
    doXPathHighlighting();
  }

  public void testDynamicContext3() throws Throwable {
    doXPathHighlighting();
  }

  public void testStringFunctionOnPath() throws Throwable {
    doXPathHighlighting();
  }

  public void testInvalidPath() throws Throwable {
    doXPathHighlighting();
  }

  public void testToNumericIDEA67335() throws Throwable {
    doXPathHighlighting();
  }

  public void testBooleanComparisonIDEA67348() throws Throwable {
    doXPathHighlighting();
  }

  public void testNumberPlusString() throws Throwable {
    doXPathHighlighting();
  }

  public void testUnaryExpressionType() throws Throwable {
    doXPathHighlighting();
  }

  public void testDatePlusDuration() throws Throwable {
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    doXPathHighlighting();
  }

  public void testDatePlusDate() throws Throwable {
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    doXPathHighlighting();
  }

  public void testDateMinusDate() throws Throwable {
    doXPathHighlighting();
  }

  public void testDateMinusDuration() throws Throwable {
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    doXPathHighlighting();
  }

  public void testDurationMinusDate() throws Throwable {
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    doXPathHighlighting();
  }

  public void testNumericDivDuration() throws Throwable {
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    doXPathHighlighting();
  }

  public void testDurationDivNumeric() throws Throwable {
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    doXPathHighlighting();
  }

  public void testNumericMultString() throws Throwable {
    doXPathHighlighting();
  }

  @Override
  protected String getSubPath() {
    return "xpath2/highlighting/types";
  }
}
