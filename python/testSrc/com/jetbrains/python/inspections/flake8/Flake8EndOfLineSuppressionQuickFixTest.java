// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.MockProblemDescriptor;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import org.intellij.lang.annotations.Language;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author jansorg
 */
public class Flake8EndOfLineSuppressionQuickFixTest extends LightPlatformCodeInsightFixture4TestCase {
  @Test
  public void testQuickFix() {
    assertFixResult(
      "x<caret> = 1",
      "x = 1  # noqa");

    assertFixResult(
      "def foo():\n    x<caret> = 1",
      "def foo():\n    x = 1  # noqa");

    assertFixResult(
      "def foo():\n    x<caret> = 1\n    \ndef bar():\n    x = 1 # noqa",
      "def foo():\n    x = 1  # noqa\n\n\ndef bar():\n    x = 1 # noqa");
  }

  private void assertFixResult(@Language("Python") String code, @Language("Python") String expectedCode) {
    myFixture.configureByText("test.py", code);

    PsiElement current = myFixture.getElementAtCaret();
    Assert.assertNotNull(current);

    SuppressQuickFix fix = new Flake8EndOfLineSuppressionQuickFix();
    Assert.assertTrue(fix.isAvailable(getProject(), current));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      ProblemDescriptor descriptor = new MockProblemDescriptor(current, "", ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      fix.applyFix(getProject(), descriptor);
    });

    Assert.assertEquals(expectedCode, myFixture.getFile().getText());
  }
}