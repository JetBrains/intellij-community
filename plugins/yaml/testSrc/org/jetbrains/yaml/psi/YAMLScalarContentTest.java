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

  public void testPlainScalar3Tag() {
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

  // Test presence of comment in literal style block scalar header
  public void testLiteralStyleCommentInHeader() {
    doTest();
  }

  // Test scalar value in case of invalid symbols in literal style block scalar header
  // Now invalid symbols will be ignored in scalar value calculation
  public void testLiteralStyleHeaderError() {
    doTest();
  }

  // Test indentation indicator in literal style block scalar header
  public void testLiteralStyleExplicitIndent() {
    doTest();
  }

  // Test strip literal block scalar chomping indicator
  public void testLiteralStyleStrip() {
    doTest();
  }

  // Test keep literal block scalar chomping indicator
  public void testLiteralStyleKeep() {
    doTest();
  }

  // Test empty literal style scalar
  public void testLiteralStyleEmpty() {
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

  public void testFoldedStyle4Tag() {
    doTest();
  }

  public void testFoldedStyle5() {
    doTest();
  }

  public void testFoldedStyle6() {
    doTest();
  }

  // Test presence of comment in folded style block scalar header
  public void testFoldedStyleCommentInHeader() {
    doTest();
  }

  // Test scalar value in case of invalid symbols in literal style block scalar header
  // Now invalid symbols will be ignored in scalar value calculation
  public void testFoldedStyleHeaderError() {
    doTest();
  }

  // Test indentation indicator in folded style block scalar header
  public void testFoldedStyleExplicitIndent() {
    doTest();
  }

  // Test strip folded block scalar chomping indicator
  public void testFoldedStyleStrip() {
    doTest();
  }

  // Test keep folded block scalar chomping indicator
  public void testFoldedStyleKeep() {
    doTest();
  }

  // Test empty folded style scalar
  public void testFoldedStyleEmpty() {
    doTest();
  }

  public void testSingleQuote1() {
    doTest();
  }

  public void testSingleQuote1Tag() {
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

  public void testDoubleQuote4() {
    doTest();
  }

  public void testDoubleQuoteTag() {
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
