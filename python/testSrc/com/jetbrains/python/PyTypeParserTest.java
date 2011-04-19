package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.types.*;

/**
 * @author yole
 */
public class PyTypeParserTest extends PyLightFixtureTestCase {
  public void testClassType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "MyObject");
    assertClassType(type, "MyObject");
  }

  private static void assertClassType(PyType type, final String name) {
    assertEquals(name, ((PyClassType) type).getPyClass().getName());
  }

  public void testTupleType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyTupleType type = (PyTupleType)PyTypeParser.getTypeByName(myFixture.getFile(), "(str, MyObject)");
    assertEquals(2, type.getElementCount());
    assertClassType(type.getElementType(0), "str");
    assertClassType(type.getElementType(1), "MyObject");
  }

  public void testListType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyCollectionType type = (PyCollectionType) PyTypeParser.getTypeByName(myFixture.getFile(), "list of MyObject");
    assertClassType(type, "list");
    assertClassType(type.getElementType(TypeEvalContext.fast()), "MyObject");
  }

  public void testDictType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyCollectionType type = (PyCollectionType) PyTypeParser.getTypeByName(myFixture.getFile(), "dict from string to MyObject");
    assertClassType(type, "dict");
    assertClassType(type.getElementType(TypeEvalContext.fast()), "MyObject");
  }
}
