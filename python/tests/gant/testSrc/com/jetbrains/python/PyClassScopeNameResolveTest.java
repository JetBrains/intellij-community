// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A class body is a scope that does not inherit names: {@code class B(A): Y = X} raises
 * {@code NameError} even when {@code A.X} exists. Attribute access and name lookup are
 * different mechanisms.
 */
@Subsystems.CodeInsight
@Layers.Functional
public class PyClassScopeNameResolveTest extends PyTestCase {

  // PY-94210
  public void testClassBodyDoesNotSeeNameInheritedFromBase() {
    assertNotResolvedInClassScope("B", "X",
                                  """
                                    class A:
                                        X = 1

                                    class B(A):
                                        pass
                                    """);
  }

  // PY-94210
  public void testClassBodyDoesNotSeeNameInheritedThroughTwoBases() {
    assertNotResolvedInClassScope("C", "X",
                                  """
                                    class A:
                                        X = 1

                                    class B(A):
                                        pass

                                    class C(B):
                                        pass
                                    """);
  }

  // PY-94210: `mro` comes from the metaclass `type`, not from `A`.
  public void testClassBodyDoesNotSeeMetaclassMethod() {
    assertNotResolvedInClassScope("A", "mro",
                                  """
                                    class A:
                                        pass
                                    """);
  }

  public void testClassBodySeesItsOwnName() {
    assertResolvedInClassScope("A", "X",
                               """
                                 class A:
                                     X = 1
                                 """);
  }

  public void testClassBodySeesItsOwnMethod() {
    assertResolvedInClassScope("A", "m",
                               """
                                 class A:
                                     def m(self):
                                         pass
                                 """);
  }

  // Type parameters are the one inherited-looking name a class body does bind.
  public void testClassBodySeesItsOwnTypeParameter() {
    assertResolvedInClassScope("G", "T",
                               """
                                 class G[T]:
                                     pass
                                 """);
  }

  private void assertNotResolvedInClassScope(@NotNull String className, @NotNull String name, @NotNull String text) {
    List<PsiElement> resolved = resolveInClassScope(className, name, text);
    assertEmpty("`" + name + "` must not resolve in the body of `" + className + "`", resolved);
  }

  private void assertResolvedInClassScope(@NotNull String className, @NotNull String name, @NotNull String text) {
    List<PsiElement> resolved = resolveInClassScope(className, name, text);
    assertFalse("`" + name + "` must resolve in the body of `" + className + "`", resolved.isEmpty());
  }

  private @NotNull List<PsiElement> resolveInClassScope(@NotNull String className,
                                                        @NotNull String name,
                                                        @NotNull String text) {
    PyFile pyFile = (PyFile)myFixture.configureByText("a.py", text);
    PyClass pyClass = pyFile.findTopLevelClass(className);
    assertNotNull(className, pyClass);
    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), pyFile);
    return PyResolveUtil.resolveQualifiedNameInScope(QualifiedName.fromDottedString(name), pyClass, context);
  }
}
