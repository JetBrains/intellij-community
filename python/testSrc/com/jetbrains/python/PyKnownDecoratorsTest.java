// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.idea.TestFor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.codeInsight.decorator.PyDecoratedFunctionTypeProvider;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementKnownDecorators;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecorator;
import com.jetbrains.python.psi.PyKnownDecoratorProvider;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyKnownDecorators;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Tests the known-decorator aggregate itself, independent of any library.
 *
 * <p>The candidates of one decorator are the known decorators it <em>may</em> resolve to, so every predicate
 * over them folds with "any". A fake provider supplies two candidates that share a short name, the shape the
 * stub-only resolve path produces for a real import.
 */
@Subsystems.CodeInsight
@Layers.Functional
public class PyKnownDecoratorsTest extends PyTestCase {

  // Two decorators with one short name and a shared first component: `from faketyping import fakefinal`
  // matches neither fully qualified name, so the short-name fallback yields both as candidates.
  private static final PyKnownDecorator FAKE_FINAL_ABSTRACT =
    new PyKnownDecorator.Builder("faketyping.a.fakefinal").setAbstract().build();
  private static final PyKnownDecorator FAKE_FINAL_PLAIN =
    new PyKnownDecorator.Builder("faketyping.b.fakefinal").build();
  // Matches the short name of a property accessor, so only the accessor guard keeps `@x.setter` from being known.
  private static final PyKnownDecorator FAKE_SETTER =
    new PyKnownDecorator.Builder("faketyping.setter").build();

  private static final PyKnownDecorator FAKE_SKIPPED_WRAP = new PyKnownDecorator.Builder("a.skippedwrap").build();

  public static final class FakeProvider implements PyKnownDecoratorProvider {
    @Override
    public @NotNull Collection<PyKnownDecorator> getKnownDecorators() {
      return List.of(FAKE_FINAL_ABSTRACT, FAKE_FINAL_PLAIN, FAKE_SETTER, FAKE_SKIPPED_WRAP);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionPointName<PyKnownDecoratorProvider> ep = PyKnownDecoratorProvider.EP_NAME;
    List<PyKnownDecoratorProvider> providers = ContainerUtil.append(ep.getExtensionList(), new FakeProvider());
    ExtensionTestUtil.maskExtensions(ep, providers, getTestRootDisposable());
  }

  /** The stub-only context never switches to AST, which is the path that produces several candidates. */
  private @NotNull PyDecorator firstDecoratorOf(@NotNull String functionName, @NotNull String text) {
    PyFile file = (PyFile)myFixture.configureByText("a.py", text);
    PyFunction function = file.findTopLevelFunction(functionName);
    assertNotNull(function);
    PyDecorator[] decorators = function.getDecoratorList().getDecorators();
    assertTrue(decorators.length > 0);
    return decorators[0];
  }

  /**
   * The fallback context is built with no origin file and no stub-to-AST permission, so {@code maySwitchToAST}
   * is false for every element — exactly the path that resolves a decorator through the imports of its file
   * and can yield several candidates.
   */
  private @NotNull TypeEvalContext stubOnlyContext() {
    return TypeEvalContext.codeInsightFallback(myFixture.getProject());
  }

  @TestFor(issues = "PY-93003", classes = PyKnownDecorators.class)
  public void testTraitOnOneOfSeveralCandidatesHolds() {
    PyDecorator decorator = firstDecoratorOf("f", """
      from faketyping import fakefinal

      @fakefinal
      def f(): ...
      """);
    PyKnownDecorators candidates = PyKnownDecoratorUtil.knownDecoratorsOf(decorator, stubOnlyContext());

    assertTrue("The resolve is ambiguous, so both candidates are expected", candidates.contains(FAKE_FINAL_ABSTRACT));
    assertTrue(candidates.contains(FAKE_FINAL_PLAIN));
    assertTrue("A trait holds when it holds for any candidate, not for all of them",
               candidates.hasTrait(PyKnownDecorator::isAbstract));
  }

  public void testDecoratorWithoutQualifiedNameIsNotKnown() {
    PyDecorator decorator = firstDecoratorOf("f", """
      @(lambda fn: fn)
      def f(): ...
      """);
    PyKnownDecorators candidates = PyKnownDecoratorUtil.knownDecoratorsOf(decorator, stubOnlyContext());

    assertFalse(candidates.isKnown());
    assertFalse(candidates.hasTrait(PyKnownDecorator::isAbstract));
  }

  // Resolving a property accessor would recurse back into property resolution, so these are never known.
  // FAKE_SETTER makes the short name `setter` known, so only the accessor guard can keep this decorator unknown.
  public void testPropertyAccessorDecoratorIsNotKnown() {
    PyFile file = (PyFile)myFixture.configureByText("a.py", """
      class C:
          @property
          def x(self): ...

          @x.setter
          def x(self, value): ...
      """);
    Collection<PyDecorator> decorators = PsiTreeUtil.findChildrenOfType(file, PyDecorator.class);
    PyDecorator setter = ContainerUtil.find(decorators, d -> "x.setter".equals(String.valueOf(d.getQualifiedName())));
    assertNotNull(setter);

    assertFalse(PyKnownDecoratorUtil.knownDecoratorsOf(setter, stubOnlyContext()).isKnown());
  }

  private @NotNull PyFunction functionOf(@NotNull String functionName, @NotNull String text) {
    PyFile file = (PyFile)myFixture.configureByText("a.py", text);
    PyFunction function = file.findTopLevelFunction(functionName);
    assertNotNull(function);
    return function;
  }

  public void testElementWithoutDecoratorsHasAllDecoratorsKnown() {
    PyFunction function = functionOf("f", """
      def f(): ...
      """);
    PyElementKnownDecorators decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext());

    assertTrue("An element with no decorator has no unknown one", decorators.allKnown());
    assertFalse(PyKnownDecoratorUtil.hasUnknownDecorator(function, stubOnlyContext()));
  }

  public void testUnknownDecoratorMakesNotAllKnown() {
    PyFunction function = functionOf("f", """
      from faketyping import fakefinal

      @fakefinal
      @totally_unknown
      def f(): ...
      """);
    PyElementKnownDecorators decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext());

    assertFalse(decorators.allKnown());
    assertTrue("A known decorator is still found among the others", decorators.contains(FAKE_FINAL_ABSTRACT));
  }

  // The size comparison this replaced collapsed two decorators of one short name into a single group
  // and wrongly reported an unknown decorator.
  @TestFor(issues = "PY-93003", classes = PyElementKnownDecorators.class)
  public void testTwoDecoratorsSharingShortNameAreAllKnown() {
    PyFunction function = functionOf("f", """
      from faketyping import fakefinal

      @fakefinal
      @fakefinal
      def f(): ...
      """);
    PyElementKnownDecorators decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext());

    assertTrue("Both decorators resolve to a known decorator", decorators.allKnown());
    assertFalse(PyKnownDecoratorUtil.hasUnknownDecorator(function, stubOnlyContext()));
  }

  // The decorator nearest the function decides, so @classmethod wins over the @staticmethod wrapping it.
  @TestFor(issues = "PY-93003", classes = PyElementKnownDecorators.class)
  public void testNearestDecoratorWithTraitWins() {
    PyFunction function = functionOf("f", """
      @staticmethod
      @classmethod
      def f(): ...
      """);
    PyElementKnownDecorators decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext());

    PyKnownDecorator nearest =
      decorators.findLastWithTrait(it -> it.isClassMethod() || it.isStaticMethod());
    assertEquals(PyKnownDecorator.CLASSMETHOD, nearest);
  }

  @TestFor(classes = PyDecoratedFunctionTypeProvider.class)
  public void testKnownDecoratorSkipsTypedCallByDefault() {
    var file = (PyFile)myFixture.configureByText("a.py", """
      from typing import Callable

      def skippedwrap(fn) -> Callable[[], int]: ...

      @skippedwrap
      def g() -> str: ...

      expr = g()
      """);
    var expr = file.findTopLevelAttribute("expr");
    assertNotNull(expr);

    assertType("str", expr, TypeEvalContext.codeAnalysis(myFixture.getProject(), file));
  }
}
