/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyResolveCalleeTest extends PyTestCase {

  @NotNull
  private PyCallExpression.PyMarkedCallee resolveCallee() {
    final PsiReference ref = myFixture.getReferenceAtCaretPosition("/resolve/callee/" + getTestName(false) + ".py");
    final PyCallExpression call = PsiTreeUtil.getParentOfType(ref.getElement(), PyCallExpression.class);

    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);

    final List<PyCallExpression.PyMarkedCallee> callees = call.multiResolveCallee(resolveContext);
    assertEquals(1, callees.size());

    return callees.get(0);
  }

  public void testInstanceCall() {
    final PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testClassCall() {
    final PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(null, resolved.getModifier());
  }

  public void testDecoCall() {
    final PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testDecoParamCall() {
    final PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertNull(resolved.getModifier());
  }
  
  public void testWrappedStaticMethod() {
    final PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(0, resolved.getImplicitOffset());
    assertEquals(PyFunction.Modifier.STATICMETHOD, resolved.getModifier());
  }
}
