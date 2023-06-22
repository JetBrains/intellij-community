// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyOverloadsResolutionTest extends PyTestCase {
  public void testFirstMatchingFunctionOverloadSelectedInPyFile() {
    doTest("str",
           """
             from typing import overload
             
             @overload
             def func(x: int) -> int:
                 pass
             
             @overload
             def func(x: str) -> str:
                 pass
             
             @overload
             def func(x: object) -> object:
                 pass
             
             def func(x):
                ...
             
             expr = func("foo")
             """);
  }

  public void testFirstMatchingFunctionOverloadSelectedInSiblingPyiFile() {
    doMultiFileStubAwareTest("str");
  }

  public void testFirstMatchingFunctionOverloadSelectedInImportedPyFile() {
    doMultiFileStubAwareTest("str");
  }

  public void testFirstMatchingFunctionOverloadSelectedInImportedPyiFile() {
    doMultiFileStubAwareTest("str");
  }

  public void testFirstMatchingMethodOverloadSelectedInPyFile() {
    doTest("str",
           """
             from typing import overload
             
             class C:
                 @overload
                 def method(self, x: int) -> int:
                     pass
             
                 @overload
                 def method(self, x: str) -> str:
                     pass
             
                 @overload
                 def method(self, x: object) -> object:
                     pass
             
                 def method(self, x):
                     pass
             
             expr = C().method("foo")
             """);
  }

  public void testFirstMatchingMethodOverloadSelectedInSiblingPyiFile() {
    doMultiFileStubAwareTest("str");
  }

  public void testFirstMatchingMethodOverloadSelectedInImportedPyFile() {
    doMultiFileStubAwareTest("str");
  }

  public void testFirstMatchingMethodOverloadSelectedInImportedPyiFile() {
    doMultiFileStubAwareTest("str");
  }

  public void testFirstMatchingOverloadSelectedPerUnionElement() {
    doTest("int | str",
           """
             from typing import overload
             
             class A:
                 @overload
                 def m(self) -> int:
                     pass
             
                 @overload
                 def m(self) -> list[int]:
                     pass
             
             class B:
                 @overload
                 def m(self) -> str:
                     pass
             
                 @overload
                 def m(self) -> list[int]:
                     pass
             
             inst = A() or B()
             expr = inst.m()
             """);
  }

  private void doTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  private void doMultiFileStubAwareTest(@NotNull String expectedType) {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);

    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertProjectFilesNotParsed(expr.getContainingFile());

    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/types/overloads";
  }
}
