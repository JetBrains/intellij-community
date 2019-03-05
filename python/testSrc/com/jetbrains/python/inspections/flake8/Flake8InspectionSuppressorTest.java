// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;
import org.jetbrains.annotations.NotNull;

public class Flake8InspectionSuppressorTest extends PyInspectionTestCase {

  public void testInlineComment() {
    doTestByText("def foo():\n" +
                 "    x = 1 # noqa");
  }

  public void testInlineCommentWithSuffix() {
    doTestByText("def foo():\n" +
                 "    x = 1 # noqa123   ");
  }

  public void testTopLevelComment() {
    doTestByText("# flake8: noqa\n" +
                 "def foo():\n" +
                 "    x = 1");
  }

  public void testTopLevelCommentWithSuffix() {
    doTestByText("# flake8: noqa123   \n" +
                 "def foo():\n" +
                 "    x = 1");
  }

  public void testIncompleteInlineComment() {
    doTestByText("def foo():\n" +
                 "    <weak_warning descr=\"Local variable 'x' value is not used\">x</weak_warning> = 1 # noq");
  }

  public void testUnrelatedCommentContainingFlake8() {
    doTestByText("# Mentioning flake8\n" +
                 "\n" +
                 "def foo():\n" +
                 "    <weak_warning descr=\"Local variable 'x' value is not used\">x</weak_warning> = 1\n");
  }

  public void testUnrelatedCommentContainingNoqa() {
    doTestByText("# noqa\n" +
                 "\n" +
                 "def foo():\n" +
                 "    <weak_warning descr=\"Local variable 'x' value is not used\">x</weak_warning> = 1\n");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnusedLocalInspection.class;
  }
}