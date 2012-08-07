package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.types.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyTypeParserTest extends PyTestCase {
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
    final PyCollectionType type = (PyCollectionType) PyTypeParser.getTypeByName(myFixture.getFile(), "dict from str to MyObject");
    assertNotNull(type);
    assertClassType(type, "dict");
    final PyType elementType = type.getElementType(TypeEvalContext.fast());
    assertInstanceOf(elementType, PyTupleType.class);
    final PyTupleType tupleType = (PyTupleType)elementType;
    assertEquals(2, tupleType.getElementCount());
    assertClassType(tupleType.getElementType(0), "str");
    assertClassType(tupleType.getElementType(1), "MyObject");
  }

  public void testUnionType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyUnionType type = (PyUnionType)PyTypeParser.getTypeByName(myFixture.getFile(), "MyObject or str");
    assertNotNull(type);
    final Collection<PyType> members = type.getMembers();
    assertEquals(2, members.size());
    final List<PyType> list = new ArrayList<PyType>(members);
    assertClassType(list.get(0), "MyObject");
    assertClassType(list.get(1), "str");
  }

  public void testTypeSubparts() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final String s = "list of (MyObject, collections.Iterable of MyObject, int) or None";
    PyTypeParser.ParseResult result = PyTypeParser.parse(myFixture.getFile(), s);
    assertEquals(7, result.getTypes().values().size());
  }

  public void testGenericType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "T");
    assertNotNull(type);
    assertInstanceOf(type, PyGenericType.class);
    assertEquals("T", type.getName());
  }

  // PY-4223
  public void testSphinxFormattedType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final String s = "(MyObject, :class:`MyObject`, :py:class:`MyObject`, :class:`!MyObject`, :py:class:`~MyObject`)";
    final PyTupleType type = (PyTupleType)PyTypeParser.getTypeByName(myFixture.getFile(), s);
    assertNotNull(type);
    final int n = type.getElementCount();
    assertEquals(5, n);
    for (int i = 0; i < n; i++) {
      assertClassType(type.getElementType(i), "MyObject");
    }
  }
}
