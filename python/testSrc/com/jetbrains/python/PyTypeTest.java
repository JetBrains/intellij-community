// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyNamedTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypingNewType;
import com.jetbrains.python.psi.types.PyTypingNewTypeFactoryType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * legacy, use a `PyCodeInsightTestCase` suite
 */
@Subsystems.CodeInsight
@Layers.Functional
public class PyTypeTest extends PyTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testParameterFromUsages() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    Registry.get("python.use.better.control.flow.type.inference").setValue(true);
    final String text = """
      def foo(bar):
          expr = bar
      def use_foo(x):
          foo(x)
          foo(3)
          foo('bar')
      """;
    final PyExpression expr = parseExpr(text);
    assertNotNull(expr);
    doTest("UnsafeUnion[Union[Literal[3], str], Any]", expr, TypeEvalContext.codeCompletion(expr.getProject(), expr.getContainingFile()));
  }

  // TODO: enable this test when properties will be calculated with TypeEvalContext
  public void ignoredTestAbsAbstractPropertyWithAs() {
    doTest("str",
           """
             from abc import abstractproperty as ap
             class D:
                 @ap
                 def foo(self):
                     return 'foo'
             expr = D().foo""");
  }

  public void testTypingNTInheritor() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from typing import NamedTuple
                                                    class User(NamedTuple):
                                                        name: str
                                                        level: int = 0
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          final PyType type = context.getType(definition);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyClassType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from typing import NamedTuple
                                                  class User(NamedTuple):
                                                      name: str
                                                      level: int = 0
                                                  expr = User("name")""");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          final PyType type = context.getType(instance);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyClassType.class);
        }
      }
    );
  }

  public void testTypingNTTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from typing import NamedTuple
                                                    User = NamedTuple("User", name=str, level=int)
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from typing import NamedTuple
                                                  User = NamedTuple("User", name=str, level=int)
                                                  expr = User("name")""");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          assertInstanceOf(context.getType(instance), PyNamedTupleType.class);
        }
      }
    );
  }

  public void testCollectionsNTInheritor() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from collections import namedtuple
                                                    class User(namedtuple("User", "name level")):
                                                        pass
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          final PyType type = context.getType(definition);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from collections import namedtuple
                                                  class User(namedtuple("User", "name level")):
                                                      pass
                                                  expr = User('MrRobot')""");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          final PyType type = context.getType(instance);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyClassTypeImpl.class);
        }
      }
    );
  }

  public void testCollectionsNTTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from collections import namedtuple
                                                    User = namedtuple("User", "name level")
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from collections import namedtuple
                                                  User = namedtuple("User", "name level")
                                                  expr = User('MrRobot')""");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          assertInstanceOf(context.getType(instance), PyNamedTupleType.class);
        }
      }
    );
  }

  // PY-24960
  // TODO Re-enable once PY-61090 is fixed
  public void _testOperatorReturnsAny() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Union[bool, Any]",
                   """
                     from typing import Any
                     class Bar:
                         def __eq__(self, other) -> Any:
                             pass
                     expr = (Bar() == 2)""")
    );
  }

  // PY-24240
  public void testImplicitSuper() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        final PyExpression expression = parseExpr("""
                                                    class A:
                                                        pass
                                                    expr = A""");

        for (TypeEvalContext context : getTypeEvalContexts(expression)) {
          final PyType type = context.getType(expression);
          assertInstanceOf(type, PyClassType.class);

          final PyClassType objectType = PyBuiltinCache.getInstance(expression).getObjectType();
          assertNotNull(objectType);

          assertEquals(Collections.singletonList(objectType.toClass()), ((PyClassType)type).getSuperClassTypes(context));
        }
      }
    );
  }

  // PY-25751
  public void testNotImportedModuleInDunderAll() {
    doMultiFileTest("Union[pkg.aaa, Any]",
                    "from pkg import *\n" +
                    "expr = aaa");
  }

  // PY-25751
  public void testNotImportedPackageInDunderAll() {
    doMultiFileTest("Union[pkg.aaa, Any]",
                    "from pkg import *\n" +
                    "expr = aaa");
  }

  // PY-21302
  public void testNewTypeReferenceTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from typing import NewType
                                                    UserId = NewType('UserId', int)
                                                    expr = UserId""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyTypingNewTypeFactoryType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from typing import NewType
                                                  UserId = NewType('UserId', int)
                                                  expr = UserId(12)""");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          assertInstanceOf(context.getType(instance), PyTypingNewType.class);
        }
      }
    );
  }

  // PY-28227
  public void testTypeVarTargetStub() {
    doMultiFileTest("TypeVar",
                    "from a import T\n" +
                    "expr = T");
  }

  public void testGeneratorNextType() {
    doTest("Literal[10]", """
      def f():
          yield 10
      expr = f().next()
      """);
  }

  private static List<TypeEvalContext> getTypeEvalContexts(@NotNull PyExpression element) {
    return ImmutableList.of(TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()).withTracing(),
                            TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()).withTracing());
  }

  @Nullable
  private PyExpression parseExpr(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    return myFixture.findElementByText("expr", PyExpression.class);
  }

  private static void doTest(final String expectedType, final PyExpression expr, final TypeEvalContext context) {
    assertType(expectedType, expr, context);
  }

  private void doTest(@NotNull final String expectedType, @NotNull final String text) {
    checkTypes(expectedType, parseExpr(text));
  }

  private void checkTypes(@NotNull String expectedType, @Nullable PyExpression expr) {
    assertNotNull(expr);
    for (TypeEvalContext context : getTypeEvalContexts(expr)) {
      assertType(expectedType, expr, context);
      assertProjectFilesNotParsed(context);
    }
  }

  public static final String TEST_DIRECTORY = "/types/";

  private void doMultiFileTest(@NotNull final String expectedType, @NotNull final String text) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    checkTypes(expectedType, parseExpr(text));
  }
}
