// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.idea.TestFor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyTypeCheckerInspectionTest;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyTypeVarTypeImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * legacy, use a `PyCodeInsightTestCase` suite
 */
@Subsystems.CodeInsight
@Layers.Functional
public class Py3TypeTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "/types/";

  @TestFor(issues = "PY-76659")
  public void ignoreTestDeclareAfterUse() {
    // TODO
    doTest("int | Any",
           """
             from typing import Any, TypeGuard
             
             def is_positive_integer(value: Any) -> TypeGuard[int]:
                 return isinstance(value, int) and value > 0
             
             def bar() -> object:
                 return 321
             
             def foo():
                 for i in range(1, 100):
                     if i > 1:
                         expr = x
                     x = bar()
                     if not is_positive_integer(x):
                         break
             """);
  }

  @TestFor(issues = "PY-21655")
  public void testUsageOfFunctionDecoratedWithAsyncioCoroutine() {
    doMultiFileTest("Literal[3]",
                    """
                      import asyncio
                      @asyncio.coroutine
                      def foo():
                          yield from asyncio.sleep(1)
                          return 3
                      async def bar():
                          expr = await foo()
                          return expr""");
  }

  @TestFor(issues = "PY-21655")
  public void testUsageOfFunctionDecoratedWithTypesCoroutine() {
    doMultiFileTest("Literal[3]",
                    """
                      import asyncio
                      import types
                      @types.coroutine
                      def foo():
                          yield from asyncio.sleep(1)
                          return 3
                      async def bar():
                          expr = await foo()
                          return expr""");
  }

  @TestFor(issues = "PY-26847")
  public void testAwaitOnImportedCoroutine() {
    doMultiFileTest("Any",
                    """
                      from mycoroutines import mycoroutine
                      
                      async def main():
                          expr = await mycoroutine()""");
  }

  /**
   * TODO: activate when return type information from :rtype: will be available in subclasses.
   * <p>
   * See {@code {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getReturnTypeFromSupertype}} javadoc.
   */
  public void ignoreTestReturnTypeInferenceInSubclassFromDocstring() {
    doTest("int",
           """
             class Base:
                 def test(self):        ""\"
                     :rtype: int
                     ""\"
                     pass
             
             class Subclass(Base):
                 def test(self): pass
             
             expr = Subclass().test()""");
  }

  /**
   * TODO: activate when return type information from :rtype: will be available in subclasses.
   * <p>
   * See {@code {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getReturnTypeFromSupertype}} javadoc.
   */
  public void ignoreTestReturnTypeInferenceInSubclassHierarchyFromDocstring() {
    doTest("int",
           """
             class Base:
                 def test(self):        ""\"
                     :rtype: int
                     ""\"
                     pass
             
             class Base1(Base):
                 pass
             
             class Subclass(Base1):
                 def test(self): pass
             
             expr = Subclass().test()""");
  }

  /**
   * @see #testRecursiveDictTopDown()
   * @see PyTypeCheckerInspectionTest#testRecursiveDictAttribute()
   */
  public void testRecursiveDictBottomUp() {
    String text = """
      class C:
          def f(self, x):
              self.foo = x
              self.foo = {'foo': self.foo}
              expr = self.foo
      """;
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression dict = myFixture.findElementByText("{'foo': self.foo}", PyExpression.class);
    assertExpressionType("dict[str, Any]", dict);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType("dict[str, Any]", expr);
  }

  public void testRecursiveDictTopDown() {
    String text = """
      class C:
          def f(self, x):
              self.foo = x
              self.foo = {'foo': self.foo}
              expr = self.foo
      """;
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType("dict[str, Any]", expr);
    PyExpression dict = myFixture.findElementByText("{'foo': self.foo}", PyExpression.class);
    assertExpressionType("dict[str, Any]", dict);
  }

  @TestFor(issues = "PY-54336")
  public void testCyclePreventionDuringGenericsSubstitution() {
    PyTypeVarType typeVarT = new PyTypeVarTypeImpl("T", null);
    PyTypeVarType typeVarV = new PyTypeVarTypeImpl("V", null);
    TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    PyType substituted;

    substituted = PyTypeChecker.substitute(typeVarT, new GenericSubstitutions(Map.of(typeVarT, typeVarT)), context);
    assertEquals(typeVarT, substituted);

    substituted = PyTypeChecker.substitute(typeVarT, new GenericSubstitutions(Map.of(typeVarT, typeVarV, typeVarV, typeVarT)), context);
    assertNull(substituted);

    PyCallableType callable = new PyCallableTypeImpl(List.of(), typeVarT);
    substituted = PyTypeChecker.substitute(callable, new GenericSubstitutions(Map.of(typeVarT, typeVarV, typeVarV, callable)), context);
    PyCallableType substitutedCallable = assertInstanceOf(substituted, PyCallableType.class);
    assertNull(substitutedCallable.getReturnType(context));
  }


  public void testTypeGuardCannotBeReturned() {
    myFixture.configureByText(PythonFileType.INSTANCE, """
      from typing import List
      from typing import TypeGuard
      
      def is_str_list(val: List[object]) -> TypeGuard[List[str]]:
          return all(isinstance(x, str) for x in val)
      
      
      def func1(val: List[object]):
          return is_str_list(val)
      
      def func2(val):
          expr = func1(val)                    
      """);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final Project project = expr.getProject();
    final PsiFile containingFile = expr.getContainingFile();
    final PyType type = TypeEvalContext.userInitiated(project, containingFile).getType(expr);
    assertFalse("type is instance of PyNarrowedType ", type instanceof PyNarrowedType);
  }

  @TestFor(issues = "PY-62078")
  public void ignoreTestTypeGuardAnnotation() {
    doTest("list[str]",
           """
             from typing import List
             from typing import TypeGuard
             
             
             def is_str_list(val):
                 # type: (List[object]) -> TypeGuard[List[str]]
                 return all(isinstance(x, str) for x in val)
             
             
             def func1(val: List[object]):
                 if not is_str_list(val):
                     pass
                 else:
                     expr = val
             """);
  }

  @TestFor(issues = "PY-86928")
  public void testProperlyImportedQualifiedNameInTypeHint() {
    doMultiFileTest("MyClass", """
      from lib import f
      
      expr = f()
      """);
  }

  @TestFor(issues = "PY-86928")
  public void testProperlyImportedQualifiedNameFromNamespacePackageInTypeHint() {
    doMultiFileTest("MyClass", """
      from lib import f
      
      expr = f()
      """);
  }

  @TestFor(issues = "PY-83529")
  public void testImportNestedBinarySubModule() {
    String testDir = TEST_DIRECTORY + getTestName(false);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      runWithAdditionalClassEntryInSdkRoots(testDir + "/python_stubs", () -> {
        doTest("pkg", """
          import pkg.subpkg
          expr = pkg
          """);
        doTest("pkg.subpkg", """
          import pkg.subpkg
          expr = pkg.subpkg
          """);
      });
    });
  }

  @TestFor(issues = "PY-88477")
  public void testHeterogeneousEnumValues() {
    doTest("tuple[Literal[1, \"\"], Literal[1], Literal[\"\"]]",
           """
             from enum import Enum

             class MyEnum(Enum):
                 A = 1
                 B = ""

             def f(p: MyEnum):
                 expr = p.value, MyEnum.A.value, MyEnum.B.value""");
  }

  @TestFor(issues = "PY-79198")
  public void testEnumNameLiteralValues() {
    runWithAdditionalFileInLibDir("mod.py", """
      from enum import Enum

      class E(Enum):
          a = 1
          b = 2
          c = 3
      """, (_) -> doTest(
      """
        tuple[Literal["a", "b", "c"], Literal["a", "b"], Literal["a"]]""",
      """
        from mod import E
        from typing import Literal

        def f(e1: E, e2: Literal[E.a, E.b]):
            expr = e1.name, e2.name, E.a.name
        """));
  }

  @TestFor(issues="PY-79204")
  public void testInferParameterFromDecoratorNoReturnAnnotation() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    doTest("int", """
      from typing import Callable

      def d(fn: Callable[[int], str]): ...

      @d
      def f(a):
          expr = a
      """);
  }

  @TestFor(issues="PY-79204")
  public void testInferParameterFromDecoratorUntypedInnermostFallsBackToOuter() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    doTest("int", """
      from collections.abc import Callable

      def transparent(fn): return fn

      def d(fn: Callable[[int], str]): ...

      @d
      @transparent
      def f(a):
          expr = a
      """);
  }

  @TestFor(issues="PY-79204")
  public void testInferParameterFromDecoratorKnownDecoratorDoesNotOverrideSelf() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    doTest("Self@A", """
      class A:
          @property
          def f(self):
              expr = self
      """);
  }

  @TestFor(issues="PY-12592")
  public void testListLiteralSpreadType() {
    doTest("list[int]", """
      expr = [*[1]]
      """);
  }

  @TestFor(issues="PY-12592")
  public void testStarTargetInTupleUnpackingType() {
    doTest("list[str]", """
      a = (1, "b")
      head, *tail = a
      expr = tail
      """);
  }

  @TestFor(issues="PY-12592")
  public void testNestedTailTargetAfterStarInTupleUnpackingType() {
    doTest("Literal[\"b\"]", """
      a = (1, (2, "b"))
      head, *_, (_, end) = a
      expr = end
      """);
  }

  @TestFor(issues="PY-12592")
  public void testNestedSequenceTargetInTupleUnpacking() {
    doTest("tuple[int, int]", """
      data = [[1, 2]]
      (x, y), = data
      expr = x, y
      """);
  }

  @TestFor(issues="PY-89352")
  public void testHeadTypeInHomogeneousTupleStarTargetUnpacking() {
    doTest("int", """
      a: tuple[int, ...]
      head, *tail = a
      expr = head
      """);
  }

  @TestFor(issues="PY-89352")
  public void testTailTypeInHomogeneousTupleStarTargetUnpacking() {
    doTest("list[int]", """
      a: tuple[int, ...]
      head, *tail = a
      expr = tail
      """);
  }

  @TestFor(issues = "PY-89956")
  public void testLongDefUseChainStackOverflow() {
    String code = """
                  x = 0
                  """ + "x = x\n".repeat(3000) + """
                  expr = x
                  """;
    doTest("Literal[0]", code);
  }

  private void doTest(final String expectedType, final String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    assertExpressionType(expectedType, expr);
  }

  private void assertExpressionType(@NotNull String expectedType, @NotNull PyExpression expr) {
    final Project project = expr.getProject();
    final PsiFile containingFile = expr.getContainingFile();
    assertType(expectedType, expr, TypeEvalContext.codeAnalysis(project, containingFile));
    assertProjectFilesNotParsed(containingFile);
    assertType(expectedType, expr, TypeEvalContext.userInitiated(project, containingFile));
  }

  private void doMultiFileTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    doTest(expectedType, text);
  }
}
