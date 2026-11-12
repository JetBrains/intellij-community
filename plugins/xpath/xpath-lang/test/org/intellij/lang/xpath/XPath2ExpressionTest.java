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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.xpath.psi.XPath2SequenceType;
import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.intellij.lang.xpath.psi.XPath2Type.DATE;
import static org.intellij.lang.xpath.psi.XPath2Type.DATETIME;
import static org.intellij.lang.xpath.psi.XPath2Type.DAYTIMEDURATION;
import static org.intellij.lang.xpath.psi.XPath2Type.DECIMAL;
import static org.intellij.lang.xpath.psi.XPath2Type.DOUBLE;
import static org.intellij.lang.xpath.psi.XPath2Type.INTEGER;
import static org.intellij.lang.xpath.psi.XPath2Type.TIME;
import static org.intellij.lang.xpath.psi.XPath2Type.YEARMONTHDURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class XPath2ExpressionTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath2/types"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    TestNamespaceContext.install(codeInsightFixture.get().getTestRootDisposable());
  }

  @Test
  public void testIntegerPlusInteger() {
   assertEquals(INTEGER, doTest(true));
  }

  @Test
  public void testIntegerPlusDecimal() {
    assertEquals(DECIMAL, doTest(true));
  }

  @Test
  public void testIntegerPlusDouble() {
    assertEquals(DOUBLE, doTest(true));
  }

  @Test
  public void testIntegerIdivInteger() {
    assertEquals(INTEGER, doTest(true));
  }

  @Test
  public void testIntegerDivInteger() {
    assertEquals(DECIMAL, doTest(true));
  }

  @Test
  public void testDoubleDivInteger() {
    assertEquals(DOUBLE, doTest(true));
  }

  @Test
  public void testDatePlusYmd() {
    assertEquals(DATE, doTest(true));
  }

  @Test
  public void testDatePlusDtd() {
    assertEquals(DATE, doTest(true));
  }

  @Test
  public void testTimePlusDtd() {
    assertEquals(TIME, doTest(true));
  }

  @Test
  public void testDateTimePlusYmd() {
    assertEquals(DATETIME, doTest(true));
  }

  @Test
  public void testDateTimePlusDtd() {
    assertEquals(DATETIME, doTest(true));
  }

  @Test
  public void testYmdPlusYmd() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  @Test
  public void testDtdPlusDtd() {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  @Test
  public void testDoubleMinusInteger() {
    assertEquals(DOUBLE, doTest(true));
  }

  @Test
  public void testDateMinusDate() {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  @Test
  public void testDateMinusYmd() {
    assertEquals(DATE, doTest(false));
  }

  @Test
  public void testDateMinusDtd() {
    assertEquals(DATE, doTest(false));
  }

  @Test
  public void testTimeMinusTime() {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  @Test
  public void testTimeMinusDtd() {
    assertEquals(TIME, doTest(false));
  }

  @Test
  public void testYmdMinusYmd() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  @Test
  public void testDoubleMultInteger() {
    assertEquals(DOUBLE, doTest(true));
  }

  @Test
  public void testYmdMultInteger() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  @Test
  public void testYmdMultDecimal() {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  protected XPathType doTest(boolean symmetric) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xpath2");

    final XPathExpression expression = ReadAction.computeBlocking(() -> getExpression());

    // all these cases must be green
    myFixture.checkHighlighting();

    if (symmetric && expression instanceof XPathBinaryExpression expr) {
      if (ReadAction.computeBlocking(() -> expr.getLOperand().getType() != expr.getROperand().getType())) {
        myFixture.configureByText(XPathFileType.XPATH2, ReadAction.computeBlocking(
          () -> expr.getROperand().getText() + " " + expr.getOperationSign() + " " + expr.getLOperand().getText()));

        assertEquals(ReadAction.computeBlocking(() -> getExpression().getType()), ReadAction.computeBlocking(() -> expression.getType()));

        myFixture.checkHighlighting();
      }
    }

    final XPathType type = ReadAction.computeBlocking(() -> expression.getType());
    if (type instanceof XPath2SequenceType) {
      return ((XPath2SequenceType)type).getType();
    }
    return type;
  }

  private XPathExpression getExpression() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final XPathFile file = (XPathFile)myFixture.getFile();
    final XPathExpression expression = file.getExpression();
    assertNotNull(expression);

    return expression;
  }
}
