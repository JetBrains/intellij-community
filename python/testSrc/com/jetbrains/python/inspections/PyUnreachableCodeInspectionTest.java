/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyUnreachableCodeInspectionTest extends PyInspectionTestCase {
  // All previous unreachable tests, feel free to split them
  public void testUnreachable() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doTest());
  }

  // PY-7420
  public void testWithSuppressedExceptions() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doTest());
  }

  // PY-25974
  public void testExprOrSysExitAssignedToVar() {
    doTest();
  }

  // PY-22184
  public void testWhileTrueTryBreakFinally() {
    doTest();
  }

  // PY-24750
  public void testIfFalse() {
    doTestByText(
      "if False:\n" +
      "    <warning descr=\"This code is unreachable\">a = 1</warning>\n" +
      "\n" +
      "if False:\n" +
      "    <warning descr=\"This code is unreachable\">b = 1</warning>\n" +
      "else:\n" +
      "    pass\n" +
      "\n" +
      "if False:\n" +
      "    <warning descr=\"This code is unreachable\">c = 1</warning>\n" +
      "elif d:\n" +
      "    pass\n" +
      "else:\n" +
      "    pass\n"
    );
  }

  // PY-24750
  public void testIfTrue() {
    doTestByText(
      "if True:\n" +
      "    pass\n" +
      "\n" +
      "if True:\n" +
      "    pass\n" +
      "else:\n" +
      "    <warning descr=\"This code is unreachable\">b = 1</warning>\n" +
      "\n" +
      "if True:\n" +
      "    pass\n" +
      "<warning descr=\"This code is unreachable\">elif c:\n" +
      "    d = 1</warning>\n" +
      "else:\n" +
      "    <warning descr=\"This code is unreachable\">e = 1</warning>\n"
    );
  }

  // PY-24750
  public void testIfElifTrue() {
    doTestByText(
      "if c:\n" +
      "    pass\n" +
      "elif True:\n" +
      "    pass\n" +
      "\n" +
      "if d:\n" +
      "    pass\n" +
      "elif True:\n" +
      "    pass\n" +
      "else:\n" +
      "    <warning descr=\"This code is unreachable\">e = 1</warning>\n"
    );
  }

  // PY-24750
  public void testIfElifFalse() {
    doTestByText(
      "if c:\n" +
      "    pass\n" +
      "elif False:\n" +
      "    <warning descr=\"This code is unreachable\">a = 1</warning>\n" +
      "\n" +
      "if d:\n" +
      "    pass\n" +
      "elif False:\n" +
      "    <warning descr=\"This code is unreachable\">b = 1</warning>\n" +
      "else:\n" +
      "    pass"
    );
  }

  // PY-28972
  public void testWhileTrueElse() {
    doTestByText(
      "while True:\n" +
      "    pass\n" +
      "else:\n" +
      "    <warning descr=\"This code is unreachable\">print(\"ok\")</warning>"
    );
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnreachableCodeInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
