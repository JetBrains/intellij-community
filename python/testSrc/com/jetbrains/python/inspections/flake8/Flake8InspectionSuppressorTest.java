// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyComparisonWithNoneInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import org.jetbrains.annotations.NotNull;

public class Flake8InspectionSuppressorTest extends PyTestCase {

  public void testInlineComment() {
    doTestByText("def foo():\n" +
                 "    x = 1 # noqa",
                 PyUnusedLocalInspection.class);
  }

  public void testCaseInsensitiveness() {
    doTestByText("def foo():\n" +
                 "    x = 1 # NOQA",
                 PyUnusedLocalInspection.class);
  }

  public void testInlineCommentWithSuffix() {
    doTestByText("def foo():\n" +
                 "    x = 1 # noqa123   ",
                 PyUnusedLocalInspection.class);
  }

  public void testInlineCommentFollowedByPlainText() {
    doTestByText("def foo():\n" +
                 "    x = 1 # noqa # General purpose comment",
                 PyUnusedLocalInspection.class);
  }

  public void testSuppressingNonPythonSpecificInspections() {
    doTestByText("s = 'ipaddress' # noqa",
                 SpellCheckingInspection.class);
  }

  public void testTopLevelCommentsIgnored() {
    doTestByText("""
                   # flake8: noqa
                   def foo():
                       <weak_warning descr="Local variable 'x' value is not used">x</weak_warning> = 1""",
                 PyUnusedLocalInspection.class);
  }

  public void testIncompleteInlineComment() {
    doTestByText("def foo():\n" +
                 "    <weak_warning descr=\"Local variable 'x' value is not used\">x</weak_warning> = 1 # noq",
                 PyUnusedLocalInspection.class);
  }

  public void testUnrelatedCommentContainingNoqa() {
    doTestByText("""
                   # noqa

                   def foo():
                       <weak_warning descr="Local variable 'x' value is not used">x</weak_warning> = 1
                   """,
                 PyUnusedLocalInspection.class);
  }

  public void testSingleErrorCode() {
    doTestByText("""
                   def func():
                       <weak_warning descr="Local variable 'x' value is not used">x</weak_warning> = unresolved  # noqa: F821
                   """,
                 PyUnusedLocalInspection.class, PyUnresolvedReferencesInspection.class);
  }

  public void testMultipleErrorCodes() {
    doTestByText("""
                   def func():
                       x = unresolved  # noqa: F821, F841
                   """,
                 PyUnusedLocalInspection.class, PyUnresolvedReferencesInspection.class);
  }

  public void testCommonErrorCodePrefix() {
    doTestByText("""
                   def func():
                       x = unresolved  # noqa: F8
                   """,
                 PyUnusedLocalInspection.class, PyUnresolvedReferencesInspection.class);
  }

  public void testUnrelatedErrorCode() {
    doTestByText("""
                   def func():
                       <weak_warning descr="Local variable 'x' value is not used">x</weak_warning> = <error descr="Unresolved reference 'unresolved'">unresolved</error>  # noqa: F631
                   """,
                 PyUnusedLocalInspection.class, PyUnresolvedReferencesInspection.class);
  }

  public void testUnusedImport() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    doTestByText("import sys  # noqa", PyUnusedLocalInspection.class);
  }

  // PY-16067
  public void testComparingWithNoneSuppressedByPycodestyleCode() {
    doTestByText("""
                   def func(p):
                       if p == None:  # noqa: E711
                           pass""",
                 PyComparisonWithNoneInspection.class);
  }

  private void doTestByText(@NotNull String text, @NotNull Class<? extends LocalInspectionTool>... inspections) {
    for (Class<? extends LocalInspectionTool> inspection : inspections) {
      myFixture.enableInspections(inspection);
    }
    final PsiFile currentFile = myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.checkHighlighting();
    assertSdkRootsNotParsed(currentFile);
  }
}
