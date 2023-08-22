// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.types.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class PyTypeParserTest extends PyTestCase {
  public void testClassType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "MyObject");
    assertClassType(type, "MyObject");
  }

  private static void assertClassType(PyType type, final String name) {
    assertNotNull(type);
    assertEquals(name, ((PyClassType)type).getPyClass().getName());
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
    assertNotNull(type);
    assertClassType(type, "list");
    assertClassType(type.getIteratedItemType(), "MyObject");
  }

  public void testDictType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyCollectionType type = (PyCollectionType) PyTypeParser.getTypeByName(myFixture.getFile(), "dict from str to MyObject");
    assertNotNull(type);
    assertClassType(type, "dict");
    final List<PyType> elementTypes = type.getElementTypes();
    assertClassType(elementTypes.get(0), "str");
    assertClassType(elementTypes.get(1), "MyObject");
  }

  private TypeEvalContext getTypeEvalContext() {
    return TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile());
  }

  public void testUnionType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyUnionType type = (PyUnionType)PyTypeParser.getTypeByName(myFixture.getFile(), "MyObject or str");
    assertNotNull(type);
    final Collection<PyType> members = type.getMembers();
    assertEquals(2, members.size());
    final List<PyType> list = new ArrayList<>(members);
    assertClassType(list.get(0), "MyObject");
    assertClassType(list.get(1), "str");
  }

  public void testNoneType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "None");
    assertNotNull(type);
    assertInstanceOf(type, PyNoneType.class);
  }

  public void testIntegerType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "integer");
    assertClassType(type, "int");
  }

  public void testStringType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "string");
    assertClassType(type, "str");
  }

  public void testBooleanType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "boolean");
    assertClassType(type, "bool");
  }

  public void testDictionaryType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "dictionary");
    assertClassType(type, "dict");
  }

  public void testQualifiedNotImportedType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyTypeParser.ParseResult result = PyTypeParser.parse(myFixture.getFile(), "collections.Iterable");
    final PyType type = result.getType();
    assertClassType(type, "Iterable");
    assertEquals(2, result.getTypes().size());
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
    assertInstanceOf(type, PyTypeVarType.class);
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

  // PY-7950
  public void testUnionWithUnresolved() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "Unresolved or int");
    assertNotNull(type);
    assertInstanceOf(type, PyUnionType.class);
    final List<PyType> members = new ArrayList<>(((PyUnionType)type).getMembers());
    assertEquals(2, members.size());
    assertNull(members.get(0));
    assertClassType(members.get(1), "int");
  }

  public void testUnionParamPriority() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType t1 = PyTypeParser.getTypeByName(myFixture.getFile(), "list of int or list of str");
    assertInstanceOf(t1, PyUnionType.class);
    final PyType t2 = PyTypeParser.getTypeByName(myFixture.getFile(), "list of str or int");
    assertInstanceOf(t2, PyUnionType.class);
  }

  public void testParenthesesPriority() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "list of (str or int)");
    assertInstanceOf(type, PyCollectionType.class);
    final PyCollectionType collectionType = (PyCollectionType)type;
    assertNotNull(collectionType);
    assertEquals("list", collectionType.getName());
    assertInstanceOf(collectionType.getIteratedItemType(), PyUnionType.class);
  }

  public void testBoundedGeneric() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      myFixture.configureByFile("typeParser/typeParser.py");
      final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "T <= str or unicode");
      assertNotNull(type);
      assertInstanceOf(type, PyTypeVarType.class);
      final PyTypeVarType genericType = (PyTypeVarType)type;
      final PyType bound = genericType.getBound();
      assertInstanceOf(bound, PyUnionType.class);
    });
  }

  public void testBracketSingleParam() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "list[int]");
    assertInstanceOf(type, PyCollectionType.class);
    final PyCollectionType collectionType = (PyCollectionType)type;
    assertNotNull(collectionType);
    assertEquals("list", collectionType.getName());
    assertEquals("int", collectionType.getIteratedItemType().getName());
  }

  public void testBracketMultipleParams() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "dict[str, int]");
    assertInstanceOf(type, PyCollectionType.class);
    final PyCollectionType collectionType = (PyCollectionType)type;
    assertNotNull(collectionType);
    assertEquals("dict", collectionType.getName());
    final List<PyType> elementTypes = collectionType.getElementTypes();
    assertEquals(2, elementTypes.size());
    final PyType first = elementTypes.get(0);
    assertNotNull(first);
    assertEquals("str", first.getName());
    final PyType second = elementTypes.get(1);
    assertNotNull(second);
    assertEquals("int", second.getName());
  }

  public void testUnionOrOperator() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyUnionType type = (PyUnionType)PyTypeParser.getTypeByName(myFixture.getFile(), "MyObject | str | bytes");
    assertNotNull(type);
    final Collection<PyType> members = type.getMembers();
    assertEquals(3, members.size());
    final List<PyType> list = new ArrayList<>(members);
    assertClassType(list.get(0), "MyObject");
    assertClassType(list.get(1), "str");
    assertClassType(list.get(2), "bytes");
  }

  public void testUnionOfUnion() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyUnionType type = (PyUnionType)PyTypeParser.getTypeByName(myFixture.getFile(), "(int or bytes) or (str or bytes)");
    assertNotNull(type);
    final Collection<PyType> members = type.getMembers();
    assertEquals(3, members.size());
    final List<PyType> list = new ArrayList<>(members);
    assertClassType(list.get(0), "int");
    assertClassType(list.get(1), "bytes");
    assertClassType(list.get(2), "str");
  }

  public void testCallableType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "(int, T) -> T");
    assertInstanceOf(type, PyCallableType.class);
    final PyCallableType callableType = (PyCallableType)type;
    assertNotNull(callableType);
    final TypeEvalContext context = getTypeEvalContext();
    final PyType returnType = callableType.getReturnType(context);
    assertInstanceOf(returnType, PyTypeVarType.class);
    final List<PyCallableParameter> parameterTypes = callableType.getParameters(context);
    assertNotNull(parameterTypes);
    assertEquals(2, parameterTypes.size());
    final PyType type0 = parameterTypes.get(0).getType(context);
    assertNotNull(type0);
    assertEquals("int", type0.getName());
    final PyType type1 = parameterTypes.get(1).getType(context);
    assertNotNull(type1);
    assertEquals("T", type1.getName());
  }

  public void testCallableWithoutArgs() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "() -> int");
    assertInstanceOf(type, PyCallableType.class);
    final PyCallableType callableType = (PyCallableType)type;
    assertNotNull(callableType);
    final PyType returnType = callableType.getReturnType(getTypeEvalContext());
    assertNotNull(returnType);
    assertEquals("int", returnType.getName());
    final List<PyCallableParameter> parameterTypes = callableType.getParameters(getTypeEvalContext());
    assertNotNull(parameterTypes);
    assertEquals(0, parameterTypes.size());
  }

  public void testQualifiedUserSkeletonsClass() {
    doTest("Iterator[int]", "collections.Iterator[int]");
  }

  private void doTest(final String expectedType, final String text) {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PsiFile file = myFixture.getFile();
    final PyType type = PyTypeParser.getTypeByName(file, text);
    TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file).withTracing();
    final String actualType = PythonDocumentationProvider.getTypeName(type, context);
    assertEquals(expectedType, actualType);
  }
}
