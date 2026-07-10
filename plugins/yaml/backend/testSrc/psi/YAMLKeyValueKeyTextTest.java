// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class YAMLKeyValueKeyTextTest extends BasePlatformTestCase {
  public void testPlainKey() {
    doTest("""
             fo<caret>o: bar""",
           "foo");
  }

  public void testQuotedBracketKey() {
    doTest("""
             props:
               '[fo<caret>o]': bar""",
           "[foo]");
  }

  public void testUnquotedFlowSequenceKey() {
    doTest("""
             props:
               [fo<caret>o]: bar""",
           "[foo]");
  }

  public void testUnquotedFlowSequenceKeyWithDots() {
    doTest("""
             props:
               [com.example.f<caret>oo]: bar""",
           "[com.example.foo]");
  }

  private void doTest(@NotNull String yamlWithCaret, @NotNull String expectedKeyText) {
    myFixture.configureByText("test.yml", yamlWithCaret);

    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    final YAMLKeyValue keyValue = PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, YAMLKeyValue.class);
    assertNotNull(keyValue);

    assertEquals(expectedKeyText, keyValue.getKeyText());
  }
}
