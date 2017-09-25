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

import org.intellij.lang.xpath.psi.XPath2SequenceType;
import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;

import static org.intellij.lang.xpath.psi.XPath2Type.*;

public class XPath2ExpressionTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
  }

  public void testIntegerPlusInteger() {
   assertEquals(INTEGER, doTest(true));
  }

  public void testIntegerPlusDecimal() {
    assertEquals(DECIMAL, doTest(true));
  }

  public void testIntegerPlusDouble() {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testIntegerIdivInteger() {
    assertEquals(INTEGER, doTest(true));
  }

  public void testIntegerDivInteger() {
    assertEquals(DECIMAL, doTest(true));
  }

  public void testDoubleDivInteger() {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testDatePlusYmd() {
    assertEquals(DATE, doTest(true));
  }

  public void testDatePlusDtd() {
    assertEquals(DATE, doTest(true));
  }

  public void testTimePlusDtd() {
    assertEquals(TIME, doTest(true));
  }

  public void testDateTimePlusYmd() {
    assertEquals(DATETIME, doTest(true));
  }

  public void testDateTimePlusDtd() {
    assertEquals(DATETIME, doTest(true));
  }

  public void testYmdPlusYmd() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  public void testDtdPlusDtd() {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  public void testDoubleMinusInteger() {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testDateMinusDate() {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  public void testDateMinusYmd() {
    assertEquals(DATE, doTest(false));
  }

  public void testDateMinusDtd() {
    assertEquals(DATE, doTest(false));
  }

  public void testTimeMinusTime() {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  public void testTimeMinusDtd() {
    assertEquals(TIME, doTest(false));
  }

  public void testYmdMinusYmd() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  public void testDoubleMultInteger() {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testYmdMultInteger() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  public void testYmdMultDecimal() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  protected XPathType doTest(boolean symmetric) {
    myFixture.configureByFile(getTestFileName() + ".xpath2");

    final XPathExpression expression = getExpression();

    // all these cases must be green
    myFixture.checkHighlighting();

    if (symmetric && expression instanceof XPathBinaryExpression) {
      final XPathBinaryExpression expr = (XPathBinaryExpression)expression;
      if (expr.getLOperand().getType() != expr.getROperand().getType()) {
        myFixture.configureByText(XPathFileType.XPATH2,
                                  expr.getROperand().getText() + " " + expr.getOperationSign() + " " + expr.getLOperand().getText());

        assertEquals(getExpression().getType(), expression.getType());

        myFixture.checkHighlighting();
      }
    }

    final XPathType type = expression.getType();
    if (type instanceof XPath2SequenceType) {
      return ((XPath2SequenceType)type).getType();
    }
    return type;
  }

  private XPathExpression getExpression() {
    final XPathFile file = (XPathFile)myFixture.getFile();
    final XPathExpression expression = file.getExpression();
    assertNotNull(expression);

    return expression;
  }

  @Override
  protected String getSubPath() {
    return "xpath2/types";
  }
}
