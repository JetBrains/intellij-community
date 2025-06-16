// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public final class PyTypeConversionTest extends PyTestCase {
  public void testCustomIterableClassToTypingIterable() {
    doTest("typing.Iterable", "Iterable[str]", """
      from typing import Iterator
      
      class Class:
          def __next__(self) -> str:
              return "foo"
          def __iter__(self) -> Iterator[str]:
              return self
      
      expr = Class()
      """);
  }

  public void testDictToTypingMapping() {
    doTest("typing.Mapping", "Mapping[str, int]", """
      expr = {"foo": 42}
      """);
  }

  public void testTupleToTypingIterable() {
    doTest("typing.Iterable", "Iterable[int | str]", """
      expr = (1, "foo")
      """);
  }

  public void testCustomContextManagerClassToContextlibAbstractContextManager() {
    doTest("contextlib.AbstractContextManager", "AbstractContextManager[int, bool | None]", """
      class CustomManager:
          def __enter__(self) -> int:
              return 42
      
          def __exit__(self, exc_type, exc_val, exc_tb) -> bool | None:
              pass
      
      expr = CustomManager()
      """);
  }

  public void testContextManagerGeneratorToContextlibAbstractContextManager() {
    doTest("contextlib.AbstractContextManager", "AbstractContextManager[str, bool | None]", """
      import contextlib
      
      @contextlib.contextmanager
      def f():
          yield "foo"
      
      expr = f()
      """);
  }

  public void doTest(@NotNull String superTypeFqn, @NotNull String expectedResultType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    TypeEvalContext context = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    PyType classType = assertInstanceOf(context.getType(expr), PyClassType.class);
    PyType converted = PyTypeUtil.convertToType(classType, superTypeFqn, expr, context);
    String actualType = PythonDocumentationProvider.getTypeName(converted, context);
    assertEquals(expectedResultType, actualType);
  }
}
