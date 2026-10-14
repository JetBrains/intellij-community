// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.idea.TestFor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecorator;
import com.jetbrains.python.psi.PyKnownDecoratorProvider;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyKnownDecorators;
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
  // setDeclaredTypeApplied / isDeclaredTypeApplied are renamed to setTypedResult / hasTypedResult in Task 3.
  private static final PyKnownDecorator FAKE_FINAL_TYPED =
    new PyKnownDecorator.Builder("faketyping.a.fakefinal").setDeclaredTypeApplied().build();
  private static final PyKnownDecorator FAKE_FINAL_PLAIN =
    new PyKnownDecorator.Builder("faketyping.b.fakefinal").build();

  public static final class FakeProvider implements PyKnownDecoratorProvider {
    @Override
    public @NotNull Collection<PyKnownDecorator> getKnownDecorators() {
      return List.of(FAKE_FINAL_TYPED, FAKE_FINAL_PLAIN);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionPointName<PyKnownDecoratorProvider> ep = ExtensionPointName.create("Pythonid.knownDecoratorProvider");
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

    assertTrue("The resolve is ambiguous, so both candidates are expected", candidates.contains(FAKE_FINAL_TYPED));
    assertTrue(candidates.contains(FAKE_FINAL_PLAIN));
    assertTrue("A trait holds when it holds for any candidate, not for all of them",
               candidates.hasTrait(PyKnownDecorator::isDeclaredTypeApplied));
  }

  public void testDecoratorWithoutQualifiedNameIsNotKnown() {
    PyDecorator decorator = firstDecoratorOf("f", """
      @(lambda fn: fn)
      def f(): ...
      """);
    PyKnownDecorators candidates = PyKnownDecoratorUtil.knownDecoratorsOf(decorator, stubOnlyContext());

    assertFalse(candidates.isKnown());
    assertFalse(candidates.hasTrait(PyKnownDecorator::isDeclaredTypeApplied));
    assertNull(candidates.findWithTrait(PyKnownDecorator::isDeclaredTypeApplied));
  }

  // Resolving a property accessor would recurse back into property resolution, so these are never known.
  public void testPropertyAccessorDecoratorIsNotKnown() {
    PyFile file = (PyFile)myFixture.configureByText("a.py", """
      class C:
          @property
          def x(self): ...

          @x.setter
          def x(self, value): ...
      """);
    PyFunction setter = file.findTopLevelClass("C").getMethods()[1];
    PyDecorator accessor = setter.getDecoratorList().getDecorators()[0];

    assertFalse(PyKnownDecoratorUtil.knownDecoratorsOf(accessor, stubOnlyContext()).isKnown());
  }
}
