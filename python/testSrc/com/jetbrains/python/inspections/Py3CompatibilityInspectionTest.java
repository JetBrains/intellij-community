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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class Py3CompatibilityInspectionTest extends PyInspectionTestCase {

  // PY-80237
  public void testBreakInFinallyBlock() {
    doTestByText("""
                 while True:
                   try:
                     print("a")
                   finally:
                     <error descr="Python version 3.14 does not support 'break' inside 'finally' clause">break</error>"""
    );
  }

  // PY-80237
  public void testReturnInFinallyBlock() {
    doTestByText("""
                 def foo():
                   try:
                     pass
                   finally:
                     <error descr="Python version 3.14 does not support 'return' inside 'finally' clause">return</error>"""
    );
  }


  // PY-80237
  public void testReturnInFunctionDefinitionInFinallyBlock() {
    doTestByText("""
                 try:
                   pass
                 finally:
                   def f():
                     return 42"""
    );
  }

  // PY-80237
  public void testBreakInLoopInFinallyBlock() {
    doTestByText("""
                 try:
                   pass
                 finally:
                   for x in [1, 2, 3]:
                     break"""
    );
  }

  // PY-18965
  public void testExec() {
    doTest();
  }

  // PY-44974
  public void testBitwiseOrUnionOnReturnType() {
    doTestByText("""
                   def foo() -> <warning descr="Python versions 2.7, 3.7, 3.8, 3.9 do not allow writing union types as X | Y">int | str</warning>:
                       return 42
                   """);
  }

  // PY-44974
  public void testBitwiseOrUnionOnReturnTypeFromFeatureAnnotations() {
    doTestByText("""
                   from __future__ import annotations
                   def foo() -> int | str:
                       return 42
                   """);
  }

  // PY-44974
  public void testBitwiseOrUnionOnIsInstance() {
    doTestByText("""
                   class A:
                       pass

                   assert isinstance(A(), <warning descr="Python versions 2.7, 3.7, 3.8, 3.9 do not allow writing union types as X | Y">int | str</warning>)
                   """);
  }

  // PY-44974
  public void testBitwiseOrUnionInPrint() {
    doTestByText("print(<warning descr=\"Python versions 2.7, 3.7, 3.8, 3.9 do not allow writing union types as X | Y\">int | str | dict</warning>)");
  }

  // PY-44974
  public void testBitwiseOrUnionInOverloadedOperator() {
    doTestByText("""
                   class A:
                     def __or__(self, other) -> int: return 5
                    \s
                   expr = A() | A()""");
  }

  // PY-44974
  public void testBitwiseOrUnionInParenthesizedUnionOfUnions() {
    doTestByText("""
                   def foo() -> <warning descr="Python versions 2.7, 3.7, 3.8, 3.9 do not allow writing union types as X | Y">int | ((list | dict) | (float | str))</warning>:
                       pass
                   """);
  }

  // PY-48012
  public void testMatchStatement() {
    doTest();
  }

  // PY-81022
  public void testCollectionsAbc() {
    doTestByText("import <warning descr=\"Python version 2.7 does not have module collections.abc\">collections.abc</warning>");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyCompatibilityInspection.class;
  }

  @Override
  protected String getTestCaseDirectory() {
    return "inspections/PyCompatibilityInspection3K/";
  }
}
