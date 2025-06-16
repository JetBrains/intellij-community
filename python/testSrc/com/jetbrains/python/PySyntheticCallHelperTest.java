// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

public class PySyntheticCallHelperTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "/types/syntheticCallHelper/";

  public void testSimpleFunctionOnTopLevel() {
    doTest("int", """
      def foo(x) -> int:
        pass
      """, () -> {
      PyFunction function = myFixture.findElementByText("foo", PyFunction.class);
      return PySyntheticCallHelper.getCallType(function, null, List.of(PyBuiltinCache.getInstance(function).getNoneType()),
                                               TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()));
    });
  }

  public void testSimpleFunctionOnTopLevelTooFewArguments() {
    doTest("int", """
      def foo(x, y, z) -> int:
        pass
      """, () -> {
      PyFunction function = myFixture.findElementByText("foo", PyFunction.class);
      return PySyntheticCallHelper.getCallType(function, null, List.of(PyBuiltinCache.getInstance(function).getNoneType()),
                                               TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()));
    });
  }

  public void testSimpleFunctionOnTopLevelTooManyArguments() {
    doTest("int", """
      def foo(x) -> int:
        pass
      """, () -> {
      PyFunction function = myFixture.findElementByText("foo", PyFunction.class);
      return PySyntheticCallHelper.getCallType(function, null, List.of(PyBuiltinCache.getInstance(function).getNoneType(),
                                                                       PyBuiltinCache.getInstance(myFixture.getFile()).getStrType(),
                                                                       PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                               TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()));
    });
  }

  public void testSimpleFunctionWithOverloadsOnTopLevel() {
    doTest("str", """
      from typing import overload, Any
      @overload
      def foo(x: str) -> str:
        pass
      @overload
      def foo(x: int) -> int:
        pass
      def foo(x: Any) -> Any:
        pass
      """, () -> {
      PyFunction function = myFixture.findElementByText("foo", PyFunction.class);
      return PySyntheticCallHelper.getCallType(function, null, List.of(PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                               TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()));
    });
  }

  public void testClassMethod() {
    doTest("str", """
      class Clazz:
          def foo(self, x: str) -> str:
              pass
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyClass clazz = myFixture.findElementByText("Clazz", PyClass.class);
      assertInstanceOf(clazz, PyClass.class);
      PyType classType = context.getType(clazz);
      assertInstanceOf(classType, PyClassType.class);
      classType = ((PyClassType)classType).toInstance();
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo",
                                                             classType,
                                                             List.of(PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                                             context);
    });
  }

  public void testGenericMethod() {
    doTest("str", """
      def foo[T](x: T) -> T:
          pass
      """, () -> {
      PyFunction function = myFixture.findElementByText("foo", PyFunction.class);
      return PySyntheticCallHelper.getCallType(function,
                                               null,
                                               List.of(PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                               TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()));
    });
  }

  public void testClassMethodWithOverloads() {
    doTest("str", """
      from typing import overload, Any
      class Clazz:
          @overload
          def foo(self, x: str) -> str:
              pass
          @overload
          def foo(self, x: int) -> int:
              pass
          def foo(self, x: Any) -> Any:
              pass
      
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyClass clazz = myFixture.findElementByText("Clazz", PyClass.class);
      assertInstanceOf(clazz, PyClass.class);
      PyType classType = context.getType(clazz);
      assertInstanceOf(classType, PyClassType.class);
      classType = ((PyClassType)classType).toInstance();
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo",
                                                             classType,
                                                             List.of(PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                                             context);
    });
  }

  public void testClassMethodWithConditionalImpls() {
    doTest("str | int", """
      from typing import overload, Any
      class Clazz:
          if True:
            def foo(self, x: str) -> str:
                pass
          else:
            def foo(self, x: str) -> int:
                pass
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyClass clazz = myFixture.findElementByText("Clazz", PyClass.class);
      assertInstanceOf(clazz, PyClass.class);
      PyType classType = context.getType(clazz);
      assertInstanceOf(classType, PyClassType.class);
      classType = ((PyClassType)classType).toInstance();
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo",
                                                             classType,
                                                             List.of(PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                                             context);
    });
  }

  public void testClassMethodWithOverloadsByInstance() {
    doTest("str", """
      from typing import overload, Any
      class Clazz:
          @overload
          def foo(self, x: str) -> str:
              pass
          @overload
          def foo(self, x: int) -> int:
              pass
          def foo(self, x: Any) -> Any:
              pass
      clazz = Clazz()
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyTargetExpression classRef = myFixture.findElementByText("clazz", PyTargetExpression.class);
      assertInstanceOf(classRef, PyTargetExpression.class);
      PyType classType = context.getType(classRef);
      assertInstanceOf(classType, PyClassType.class);
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo",
                                                             classType,
                                                             List.of(PyBuiltinCache.getInstance(myFixture.getFile()).getStrType()),
                                                             context);
    });
  }

  public void testGenericClassMethod() {
    doTest("list", """
      class Clazz[T]:
        def foo(self) -> T:
          pass
      instance = Clazz[list]()
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyTargetExpression instance = myFixture.findElementByText("instance", PyTargetExpression.class);
      assertInstanceOf(instance, PyTargetExpression.class);
      PyType classType = context.getType(instance);
      assertInstanceOf(classType, PyClassType.class);
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo", classType, List.of(), context);
    });
  }

  public void testMethodInExternalFile() {
    doMultiFileTest("int", """
      from lib import foo
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyReferenceExpression functionRef = myFixture.findElementByText("foo", PyReferenceExpression.class);
      assertInstanceOf(functionRef, PyReferenceExpression.class);
      PsiElement resolveResult = functionRef.getReference(PyResolveContext.defaultContext(context)).resolve();
      assertInstanceOf(resolveResult, PyFunction.class);
      return PySyntheticCallHelper.getCallType((PyFunction)resolveResult, null, List.of(PyBuiltinCache.getInstance(functionRef).getNoneType()),
                                               TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile()));
    });
  }

  public void testClassMethodInExternalFile() {
    doMultiFileTest("str", """
      from lib import Clazz
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyReferenceExpression classRef = myFixture.findElementByText("Clazz", PyReferenceExpression.class);
      assertInstanceOf(classRef, PyReferenceExpression.class);
      PyType classType = context.getType(classRef);
      assertInstanceOf(classType, PyClassType.class);
      classType = ((PyClassType)classType).toInstance();
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo",
                                                             classType,
                                                             List.of(),
                                                             context);
    });
  }

  public void testGenericClassMethodInExternalFile() {
    doMultiFileTest("list", """
      from lib import Clazz
      instance = Clazz[list]()
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyTargetExpression instance = myFixture.findElementByText("instance", PyTargetExpression.class);
      assertInstanceOf(instance, PyTargetExpression.class);
      PyType classType = context.getType(instance);
      assertInstanceOf(classType, PyClassType.class);
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo", classType, List.of(), context);
    });
  }

  public void testGenericClassMethodWithOverloadsInExternalFile() {
    doMultiFileTest("float | list", """
      from lib import Clazz
      instance = Clazz[list]()
      """, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      PyTargetExpression instance = myFixture.findElementByText("instance", PyTargetExpression.class);
      assertInstanceOf(instance, PyTargetExpression.class);
      PyType classType = context.getType(instance);
      assertInstanceOf(classType, PyClassType.class);
      PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(myFixture.getFile());
      List<PyType> argumentTypes = List.of(builtinCache.getStrType(), builtinCache.getIntType(), builtinCache.getBoolType());
      return PySyntheticCallHelper.getCallTypeByFunctionName("foo", classType, argumentTypes, context);
    });
  }


  private void doTest(@NotNull String expectedType, @NotNull String text, Supplier<PyType> actualType) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyType type = actualType.get();
    assertExpressionType(expectedType, type);
  }

  private void doMultiFileTest(@NotNull String expectedType, @NotNull String text, Supplier<PyType> actualType) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest(expectedType, text, actualType);
  }

  private void assertExpressionType(@NotNull String expectedType, @NotNull PyType actualType) {
    final Project project = myFixture.getProject();
    final PsiFile containingFile = myFixture.getFile();
    assertType(expectedType, actualType, TypeEvalContext.codeAnalysis(project, containingFile));
    assertProjectFilesNotParsed(containingFile);
    assertType(expectedType, actualType, TypeEvalContext.userInitiated(project, containingFile));
  }

  public static void assertType(@NotNull String expectedType, @NotNull PyType actualType, @NotNull TypeEvalContext context) {
    assertType("Failed in " + context + " context", expectedType, actualType, context);
  }

  public static void assertType(@NotNull String message,
                                @NotNull String expectedType,
                                @NotNull PyType actualType,
                                @NotNull TypeEvalContext context) {
    final String actualTypeName = PythonDocumentationProvider.getTypeName(actualType, context);
    assertEquals(message, expectedType, actualTypeName);
  }
}
