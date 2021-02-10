// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;

import java.util.Arrays;

public class YAMLScalarLiteralEscaperTest extends BasePlatformTestCase {
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

  public void testPlainScalarBigFirstLineIndent1() {
    doTest();
  }

  public void testPlainScalarBigFirstLineIndent2() {
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

  // Test strip literal block scalar chomping indicator
  public void testLiteralStyleStrip() {
    doTest();
  }

  // Test keep literal block scalar chomping indicator
  public void testLiteralStyleKeep() {
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

  // Test presence of comment in folded style block scalar header
  public void testFoldedStyleCommentInHeader() {
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

  public void testDoubleQuote3() {
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

    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> elementLiteralEscaper = scalarElement.createLiteralTextEscaper();
    assertNotNull(elementLiteralEscaper);

    final StringBuilder builder = new StringBuilder();
    assertTrue(elementLiteralEscaper.decode(ElementManipulators.getValueTextRange(scalarElement), builder));
    assertEquals(scalarElement.getTextValue(), builder.toString());

    int[] offsets = new int[builder.length() + 1];
    for (int i = 0; i < builder.length() + 1; ++i) {
      offsets[i] = elementLiteralEscaper.getOffsetInHost(i, TextRange.from(0, scalarElement.getTextLength()));
    }

    final String elementText = scalarElement.getText();
    StringBuilder description = new StringBuilder();
    for (int i = 0; i < builder.length(); ++i) {
      description.append(builder.charAt(i))
        .append("->")
        .append(elementText.subSequence(offsets[i], offsets[i + 1]))
        .append('\n');
    }
    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".positions.txt",
                            Arrays.toString(offsets) + "\n" + description,
                            false);
  }
}
