// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author jansorg
 */
public class Flake8EndOfLineSuppressionQuickFixTest extends PyTestCase {

  public void testInlineComment() {
    doTestSuppressQuickFix(
      "def foo():\n" +
      "    <caret>x = 1",

      "def foo():\n" +
      "    x = 1  # noqa");
  }

  public void testMultilineStatementWithWhitespaces() {
    doTestSuppressQuickFix("def foo():\n" +
                           "    <caret>x = [1,\n" +
                           "         2,\n" +
                           "         3]\n",
                           "def foo():\n" +
                           "    <caret>x = [1,  # noqa\n" +
                           "         2,\n" +
                           "         3]\n");
  }

  public void testCommentCannotBeInsertedAtSameLineBecauseOfMultilineStringLiteral() {
    doTestSuppressQuickFix("def foo():\n" +
                           "    <caret>x = \"\"\"\n" +
                           "    bar\n" +
                           "    \"\"\"",
                           "def foo():\n" +
                           "    <caret>x = \"\"\"\n" +
                           "    bar\n" +
                           "    \"\"\"");
  }

  public void testCommentCannotBeInsertedAtSameLineBecauseContinuationBackslashes() {
    doTestSuppressQuickFix("def foo():\n" +
                           "    <caret>x = 'foo' \\\n" +
                           "        'bar'",
                           "def foo():\n" +
                           "    <caret>x = 'foo' \\\n" +
                           "        'bar'");
  }

  public void testExistingComment() {
    doTestSuppressQuickFix("def foo():\n" +
                           "    <caret>x = 1  # existing\n",
                           "def foo():\n" +
                           "    <caret>x = 1  # noqa # existing\n");
  }

  private void doTestSuppressQuickFix(@NotNull String before, @NotNull String after) {
    myFixture.configureByText(PythonFileType.INSTANCE, before);
    myFixture.enableInspections(PyUnusedLocalInspection.class);
    myFixture.doHighlighting();
    final IntentionAction intentionAction = myFixture.findSingleIntention("Suppress with flake8 # noqa");
    myFixture.launchAction(intentionAction);
    myFixture.checkResult(after);
  }
}