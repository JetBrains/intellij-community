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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;

public class PyClassicPropertyTest extends PyTestCase {
  protected PyClass myClass;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    prepareFile();
  }

  protected void prepareFile() {
    final PyFile file = (PyFile)myFixture.configureByFile("property/Classic.py");
    myClass = file.getTopLevelClasses().get(0);
  }

  public void testV1() {
    Property p;
    Maybe<PyCallable> accessor;
    p = myClass.findProperty("v1", true, null);
    assertNotNull(p);
    assertNull(p.getDoc());
    PyTargetExpression site = p.getDefinitionSite();
    assertEquals("v1", site.getText());

    accessor = p.getGetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals("getter", accessor.value().getName());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals("setter", accessor.value().getName());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());
  }

  public void testV2() {
    Property p;
    Maybe<PyCallable> accessor;
    p = myClass.findProperty("v2", true, null);
    assertNotNull(p);
    assertEquals("doc of v2", p.getDoc());
    PyTargetExpression site = p.getDefinitionSite();
    assertEquals("v2", site.getText());

    accessor = p.getGetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals("getter", accessor.value().getName());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals("setter", accessor.value().getName());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals("deleter", accessor.value().getName());
  }

  public void testV3() {
    Maybe<PyCallable> accessor;
    Property p = myClass.findProperty("v3", true, null);
    assertNotNull(p);
    assertNull(p.getDoc());
    PyTargetExpression site = p.getDefinitionSite();
    assertEquals("v3", site.getText());

    accessor = p.getGetter();
    assertFalse(accessor.isDefined());

    final PyType codeInsightType = p.getType(null, TypeEvalContext.codeInsightFallback(myClass.getProject()));
    assertNull(codeInsightType);

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals("deleter", accessor.value().getName());
  }

  /* NOTE: we don't support this yet
  public void testV4() throws Exception {
    Property p;
    Maybe<PyCallable> accessor;
    p = myClass.findProperty("v4");
    assertNotNull(p);
    assertEquals("otherworldly", p.getDoc());
    PyTargetExpression site = p.getDefinitionSite();
    assertEquals("otherworldly", site.getText());

    accessor = p.getGetter();
    assertFalse(accessor.isDefined());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());
  }
  */

  public void testGetProperty() {
    final PyFunction getter = myClass.findMethodByName("v5getter", false, null);
    assertNotNull(getter.getProperty());

    final PyFunction setter = myClass.findMethodByName("v5setter", false, null);
    assertNotNull(setter.getProperty());
  }

  public static class StubBasedTest extends PyClassicPropertyTest {
    @Override
    protected void prepareFile() {
      myFixture.setCaresAboutInjection(false);
      super.prepareFile();
    }
  }
}
