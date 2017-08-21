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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;

/**
 * Decorator-specific tests.
 * User: dcheryasov
 * Date: Dec 28, 2008 3:50:23 AM
 */
public class PyDecoratorTest extends PyTestCase {
  private PsiElement find() {
    PsiReference ref = myFixture.getReferenceAtCaretPosition("/resolve/decorators/" + getTestName(false) + ".py");
    return ref.getElement();
  }

  public void testDecoCall() {
    PsiElement targetElement = find().getParent();
    assertTrue(targetElement instanceof PyDecorator);
    PyDecorator deco = (PyDecorator)targetElement;
    PyFunction decofun = deco.getTarget();
    assertNotNull(decofun);
    assertEquals("foo", decofun.getName());
    assertFalse(deco.isBuiltin());
    assertFalse(deco.hasArgumentList());
  }

  public void testDecoParamCall() {
    PsiElement targetElement = find().getParent();
    assertTrue(targetElement instanceof PyDecorator);
    PyDecorator deco = (PyDecorator)targetElement;
    PyFunction decofun = deco.getTarget();
    assertNotNull(decofun);
    assertEquals("foo", decofun.getName());
    assertFalse(deco.isBuiltin());
    assertTrue(deco.hasArgumentList());
    PyArgumentList arglist = deco.getArgumentList();
    assertNotNull(arglist);
    PyExpression[] args = arglist.getArguments();
    assertEquals("argument count", 1, args.length);
    assertEquals("argument value", "1", args[0].getText());
  }
}
