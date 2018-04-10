/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.yaml.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;

import java.util.Arrays;

public class YAMLScalarLiteralEscaperTest extends LightPlatformCodeInsightFixtureTestCase {
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
    assertTrue(elementLiteralEscaper.decode(scalarElement.getTextRange(), builder));
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
