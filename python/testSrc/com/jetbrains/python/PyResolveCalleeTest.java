// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Subsystems.CodeInsight
@Layers.Functional
public class PyResolveCalleeTest extends PyTestCase {

  @NotNull
  private PyCallableType resolveCallee() {
    final PsiReference ref = myFixture.getReferenceAtCaretPosition("/resolve/callee/" + getTestName(false) + ".py");
    final PyCallExpression call = PsiTreeUtil.getParentOfType(ref.getElement(), PyCallExpression.class);

    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

    final List<PyCallableType> callees = call.multiResolveCallee(resolveContext);
    assertEquals(1, callees.size());

    return callees.get(0);
  }

  public void testInstanceCall() {
    final PyCallableType resolved = resolveCallee();
    assertNotNull(resolved.getCallable());

    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertEquals("() -> None", PythonDocumentationProvider.getTypeName(resolved, context));
  }

  public void testClassCall() {
    final PyCallableType resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(null, resolved.getModifier());
  }

  public void testDecoCall() {
    final PyCallableType resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
  }

  public void testDecoParamCall() {
    final PyCallableType resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertNull(resolved.getModifier());
  }
  
  public void testWrappedStaticMethod() {
    final PyCallableType resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(PyAstFunction.Modifier.STATICMETHOD, resolved.getModifier());

    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertEquals("(self: Any) -> None", PythonDocumentationProvider.getTypeName(resolved, context));
  }
}
