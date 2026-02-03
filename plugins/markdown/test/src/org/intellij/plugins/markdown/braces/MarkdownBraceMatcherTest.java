// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.braces;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;

public class MarkdownBraceMatcherTest extends BasePlatformTestCase {
  public void testParenBeforeIdentifier() {
    doTest('(', "<caret>abc", "(abc");
  }

  public void testParenBeforeWhiteSpace() {
    doTest('(', "<caret> abc", "() abc");
  }

  public void testParenBeforeNewLine() {
    doTest('(', "<caret>\nabc", "()\n" + "abc");
  }

  public void testParenAtTheEnd() {
    doTest('(', "<caret>", "()");
  }

  public void testBracket() {
    doTest('[', "<caret> abc", "[] abc");
  }

  public void testBracketWithSelection() {
    boolean oldSettings = CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED;
    try {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest('[', "<caret><selection>abc</selection>", "[abc]");
    } finally {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = oldSettings;
    }
  }

  public void doTest(char braceToInsert, @NotNull String text, @NotNull String expectedText) {
    myFixture.configureByText(MarkdownFileType.INSTANCE, text);
    myFixture.type(braceToInsert);
    myFixture.checkResult(expectedText);
  }
}