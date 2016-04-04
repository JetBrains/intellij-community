package org.jetbrains.yaml.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;

public class YAMLScalarContentTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/psi/data/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new YAMLParserDefinition();
  }

  public void testPlainScalar1() {
    doTest();
  }

  public void testPlainScalar2() {
    doTest();
  }

  public void testPlainScalar3() {
    doTest();
  }

  public void testLiteralStyle1() {
    doTest();
  }

  public void testLiteralStyle2() {
    doTest();
  }

  public void testLiteralStyle3() {
    doTest();
  }

  public void testFoldedStyle1() {
    doTest();
  }

  public void testFoldedStyle2() {
    doTest();
  }

  public void testFoldedStyle3() {
    doTest();
  }

  public void testFoldedStyle4() {
    doTest();
  }

  public void testSingleQuote1() {
    doTest();
  }

  public void testSingleQuote2() {
    doTest();
  }

  public void testDoubleQuote1() {
    doTest();
  }
  
  public void testDoubleQuote2() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".yml");

    final int offset = myFixture.getCaretOffset();
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(offset);
    final YAMLScalar scalarElement = PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, YAMLScalar.class);
    assertNotNull(scalarElement);

    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".txt", scalarElement.getTextValue(), false);
  }
}
