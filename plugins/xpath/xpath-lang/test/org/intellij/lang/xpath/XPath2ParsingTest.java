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

  public void testComments() {
    doXPathHighlighting();
  }

  public void testNodeComp() {
    doXPathHighlighting();
  }

  public void testNewOps() {
    doXPathHighlighting();
  }

  public void testSequence() {
    doXPathHighlighting();
  }

  public void testEmptySequence() {
    doXPathHighlighting();
  }

  public void testRange() {
    doXPathHighlighting();
  }

  public void testIfThenElse() {
    doXPathHighlighting();
  }

  public void testIfThenElseMissing() {
    doXPathHighlighting();
  }

  public void testForLoop() {
    doXPathHighlighting();
  }

  public void testSomeEvery() {
    doXPathHighlighting();
  }

  public void testTypeExpressions() {
    doXPathHighlighting();
  }

  public void testDoubleLiterals() {
    doXPathHighlighting();
  }

  public void testWildcardNamespace() {
    doXPathHighlighting();
  }

  public void testInvalidWildcard() {
    doXPathHighlighting();
  }

  public void testMissingInstanceOfType() {
    doXPathHighlighting();
  }

  public void testMissingSatisfiesExpression() {
    doXPathHighlighting();
  }

  public void testValidTypeOccurrenceIndicators() {
    doXPathHighlighting();
  }

  public void testInvalidTypeOccurrenceIndicator1() {
    doXPathHighlighting();
  }

  public void testInvalidTypeOccurrenceIndicator2() {
    doXPathHighlighting();
  }

  public void testNonKeywords() {
    doXPathHighlighting();
  }

  // IDEA-70681
  public void testCastWithMultiplication() {
    doXPathHighlighting();
  }

  // IDEA-70688
  public void testKindTestWithStar() {
    doXPathHighlighting();
  }

  public void testUnionSubExpression() {
    doXPathHighlighting();
  }

  // IDEA-67413
  private void doXPathHighlighting() {
    final String name = getTestFileName();
    myFixture.testHighlighting(false, false, false, name + ".xpath2");
  }

  @Override
  protected String getSubPath() {
    return "xpath2/parsing";
  }
}
