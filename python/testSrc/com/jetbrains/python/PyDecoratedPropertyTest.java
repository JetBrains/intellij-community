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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.toolbox.Maybe;

public class PyDecoratedPropertyTest extends PyTestCase {
  protected PyClass myClass;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON26);
    final PyFile file = (PyFile)myFixture.configureByFile("property/Decorated.py");
    myClass = file.getTopLevelClasses().get(0);
  }

  public void testW1() {
    Property p;
    Maybe<PyCallable> accessor;
    final String name = "w1";
    p = myClass.findProperty(name, true, null);
    assertNotNull(p);
    assertNull(p.getDoc());
    assertNull(p.getDefinitionSite());

    accessor = p.getGetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals(name, accessor.value().getName());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals(name, accessor.value().getName());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals(name, accessor.value().getName());
  }

  public void testW2() {
    Property p;
    Maybe<PyCallable> accessor;
    final String name = "w2";
    p = myClass.findProperty(name, true, null);
    assertNotNull(p);
    assertNull(p.getDoc());
    assertNull(p.getDefinitionSite());

    accessor = p.getGetter();
    assertTrue(accessor.isDefined());
    final PyCallable callable = accessor.value();
    assertNotNull(callable);
    assertEquals("w2", callable.getName());
    assertInstanceOf(callable, PyFunction.class);
    assertEquals("doc of " + name, ((PyFunction)callable).getDocStringExpression().getStringValue());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());
  }

}
