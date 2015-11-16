/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * Tests callee resolution in PyCallExpressionImpl.
 * User: dcheryasov
 * Date: Aug 21, 2008
 */
public class PyResolveCalleeTest extends PyTestCase {

  private PyCallExpression.PyMarkedCallee resolveCallee() {
    PsiReference ref = myFixture.getReferenceAtCaretPosition("/resolve/callee/" + getTestName(false) + ".py");
    PyCallExpression call = PsiTreeUtil.getParentOfType(ref.getElement(), PyCallExpression.class);
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    return call.resolveCallee(PyResolveContext.noImplicits().withTypeEvalContext(context));
  }

  public void testInstanceCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testClassCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(null, resolved.getModifier());
  }

  public void testDecoCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testDecoParamCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertNull(resolved.getModifier());
  }
  
  public void testWrappedStaticMethod() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(0, resolved.getImplicitOffset());
    assertEquals(resolved.getModifier(), PyFunction.Modifier.STATICMETHOD);
  }
}
