package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Maybe;

public class PyClassicPropertyTest extends PyTestCase {
  protected PyClass myClass;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final PyFile file = (PyFile)myFixture.configureByFile("property/Classic.py");
    myClass = file.getTopLevelClasses().get(0);
  }

  public void testV1() throws Exception {
    Property p;
    Maybe<PyFunction> accessor;
    p = myClass.findProperty("v1");
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

  public void testV2() throws Exception {
    Property p;
    Maybe<PyFunction> accessor;
    p = myClass.findProperty("v2");
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

  public void testV3() throws Exception {
    Property p;
    Maybe<PyFunction> accessor;
    p = myClass.findProperty("v3");
    assertNotNull(p);
    assertNull(p.getDoc());
    PyTargetExpression site = p.getDefinitionSite();
    assertEquals("v3", site.getText());

    accessor = p.getGetter();
    assertFalse(accessor.isDefined());

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
    Maybe<PyFunction> accessor;
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

}
