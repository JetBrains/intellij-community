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

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 10.03.11
*/
public class XPath2HighlightingTest extends XPath2HighlightingTestBase {
  public void testIfInsideArgumentList() throws Throwable {
    doXPathHighlighting();
  }

  public void testConsecutiveComparison() throws Throwable {
    doXPathHighlighting();
  }

  public void testNumberFollowedByToken() throws Throwable {
    doXPathHighlighting();
  }

  public void testNumberFollowedByToken2() throws Throwable {
    doXPathHighlighting();
  }

  public void testMalformedStringLiteral() throws Throwable {
    doXPathHighlighting();
  }

  public void testQuotedStringLiteral() throws Throwable {
    doXPathHighlighting();
  }

  public void testEmptyStringLiteral() throws Throwable {
    doXPathHighlighting();
  }

  public void testNodeKindTest() throws Throwable {
    doXPathHighlighting();
  }

  public void testIncorrectNodeKindTests() throws Throwable {
    doXPathHighlighting();
  }

  public void testValidOperations() throws Throwable {
    doXPathHighlighting();
  }

  public void testValidOperations2() throws Throwable {
    TestNamespaceContext.install(getTestRootDisposable());
    doXPathHighlighting();
  }

  public void testPlusOperatorNotApplicable() throws Throwable {
    doXPathHighlighting();
  }

  public void testToOperatorNotApplicable() throws Throwable {
    doXPathHighlighting();
  }

  public void testIntersectOperatorNotApplicable() throws Throwable {
    doXPathHighlighting();
  }

  public void testUnionOperatorNotApplicable() throws Throwable {
    doXPathHighlighting();
  }

  public void testInvalidNodeTypePredicate() throws Throwable {
    doXPathHighlighting();
  }

  @Override
  protected String getSubPath() {
    return "xpath2/highlighting";
  }
}