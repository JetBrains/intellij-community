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

public class XPath2HighlightingTest extends XPath2HighlightingTestBase {
  public void testIfInsideArgumentList() {
    doXPathHighlighting();
  }

  public void testConsecutiveComparison() {
    doXPathHighlighting();
  }

  public void testNumberFollowedByToken() {
    doXPathHighlighting();
  }

  public void testNumberFollowedByToken2() {
    doXPathHighlighting();
  }

  public void testMalformedStringLiteral() {
    doXPathHighlighting();
  }

  public void testQuotedStringLiteral() {
    doXPathHighlighting();
  }

  public void testEmptyStringLiteral() {
    doXPathHighlighting();
  }

  public void testNodeKindTest() {
    doXPathHighlighting();
  }

  public void testIncorrectNodeKindTests() {
    doXPathHighlighting();
  }

  public void testValidOperations() {
    doXPathHighlighting();
  }

  public void testValidOperations2() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testPlusOperatorNotApplicable() {
    doXPathHighlighting();
  }

  public void testToOperatorNotApplicable() {
    doXPathHighlighting();
  }

  public void testIntersectOperatorNotApplicable() {
    doXPathHighlighting();
  }

  public void testUnionOperatorNotApplicable() {
    doXPathHighlighting();
  }

  public void testInvalidNodeTypePredicate() {
    doXPathHighlighting();
  }

  @Override
  protected String getSubPath() {
    return "xpath2/highlighting";
  }
}