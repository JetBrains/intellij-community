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

public class XPath2ParsingTest extends TestBase {

  public void testComments() throws Throwable {
    doXPathHighlighting();
  }

  public void testNodeComp() throws Throwable {
    doXPathHighlighting();
  }

  public void testNewOps() throws Throwable {
    doXPathHighlighting();
  }

  public void testSequence() throws Throwable {
    doXPathHighlighting();
  }

  public void testEmptySequence() throws Throwable {
    doXPathHighlighting();
  }

  public void testRange() throws Throwable {
    doXPathHighlighting();
  }

  public void testIfThenElse() throws Throwable {
    doXPathHighlighting();
  }

  public void testIfThenElseMissing() throws Throwable {
    doXPathHighlighting();
  }

  public void testForLoop() throws Throwable {
    doXPathHighlighting();
  }

  public void testSomeEvery() throws Throwable {
    doXPathHighlighting();
  }

  public void testTypeExpressions() throws Throwable {
    doXPathHighlighting();
  }

  public void testDoubleLiterals() throws Throwable {
    doXPathHighlighting();
  }

  public void testWildcardNamespace() throws Throwable {
    doXPathHighlighting();
  }

  public void testInvalidWildcard() throws Throwable {
    doXPathHighlighting();
  }

  public void testMissingInstanceOfType() throws Throwable {
    doXPathHighlighting();
  }

  public void testMissingSatisfiesExpression() throws Throwable {
    doXPathHighlighting();
  }

  public void testValidTypeOccurrenceIndicators() throws Throwable {
    doXPathHighlighting();
  }

  public void testInvalidTypeOccurrenceIndicator1() throws Throwable {
    doXPathHighlighting();
  }

  public void testInvalidTypeOccurrenceIndicator2() throws Throwable {
    doXPathHighlighting();
  }

  public void testNonKeywords() throws Throwable {
    doXPathHighlighting();
  }

  // IDEA-70681
  public void testCastWithMultiplication() throws Throwable {
    doXPathHighlighting();
  }

  // IDEA-70688
  public void testKindTestWithStar() throws Throwable {
    doXPathHighlighting();
  }

  public void testUnionSubExpression() throws Throwable {
    doXPathHighlighting();
  }

  // IDEA-67413
  private void doXPathHighlighting() throws Throwable {
    final String name = getTestFileName();
    myFixture.testHighlighting(false, false, false, name + ".xpath2");
  }

  @Override
  protected String getSubPath() {
    return "xpath2/parsing";
  }
}
