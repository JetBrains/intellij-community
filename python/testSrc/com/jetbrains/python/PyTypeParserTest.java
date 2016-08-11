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

import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.types.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

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
    assertClassType(type.getElementTypes(getTypeEvalContext()).get(0), "MyObject");
  }

  public void testDictType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyCollectionType type = (PyCollectionType) PyTypeParser.getTypeByName(myFixture.getFile(), "dict from str to MyObject");
    assertNotNull(type);
    assertClassType(type, "dict");
    final List<PyType> elementTypes = type.getElementTypes(getTypeEvalContext());
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
    // Python 2
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "string");
    assertNotNull(type);
    assertInstanceOf(type, PyUnionType.class);
    final PyUnionType unionType = (PyUnionType)type;
    final ArrayList<PyType> types = new ArrayList<>(unionType.getMembers());
    assertClassType(types.get(0), "str");
    assertClassType(types.get(1), "unicode");
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
    assertInstanceOf(type, PyGenericType.class);
    assertEquals("TypeVar('T')", type.getName());
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
    final List<PyType> elementTypes = collectionType.getElementTypes(TypeEvalContext.codeInsightFallback(null));
    assertInstanceOf(elementTypes.get(0), PyUnionType.class);
  }

  public void testBoundedGeneric() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "T <= str or unicode");
    assertNotNull(type);
    assertInstanceOf(type, PyGenericType.class);
    final PyGenericType genericType = (PyGenericType)type;
    final PyType bound = genericType.getBound();
    assertInstanceOf(bound, PyUnionType.class);
  }

  public void testBracketSingleParam() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "list[int]");
    assertInstanceOf(type, PyCollectionType.class);
    final PyCollectionType collectionType = (PyCollectionType)type;
    assertNotNull(collectionType);
    assertEquals("list", collectionType.getName());
    final List<PyType> elementTypes = collectionType.getElementTypes(TypeEvalContext.codeInsightFallback(null));
    assertEquals("int", elementTypes.get(0).getName());
  }

  public void testBracketMultipleParams() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "dict[str, int]");
    assertInstanceOf(type, PyCollectionType.class);
    final PyCollectionType collectionType = (PyCollectionType)type;
    assertNotNull(collectionType);
    assertEquals("dict", collectionType.getName());
    final List<PyType> elementTypes = collectionType.getElementTypes(TypeEvalContext.codeInsightFallback(null));
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
    final PyUnionType type = (PyUnionType)PyTypeParser.getTypeByName(myFixture.getFile(), "MyObject | str | unicode");
    assertNotNull(type);
    final Collection<PyType> members = type.getMembers();
    assertEquals(3, members.size());
    final List<PyType> list = new ArrayList<>(members);
    assertClassType(list.get(0), "MyObject");
    assertClassType(list.get(1), "str");
    assertClassType(list.get(2), "unicode");
  }

  public void testCallableType() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.getTypeByName(myFixture.getFile(), "(int, T) -> T");
    assertInstanceOf(type, PyCallableType.class);
    final PyCallableType callableType = (PyCallableType)type;
    assertNotNull(callableType);
    final TypeEvalContext context = getTypeEvalContext();
    final PyType returnType = callableType.getReturnType(context);
    assertInstanceOf(returnType, PyGenericType.class);
    final List<PyCallableParameter> parameterTypes = callableType.getParameters(context);
    assertNotNull(parameterTypes);
    assertEquals(2, parameterTypes.size());
    final PyType type0 = parameterTypes.get(0).getType(context);
    assertNotNull(type0);
    assertEquals("int", type0.getName());
    final PyType type1 = parameterTypes.get(1).getType(context);
    assertNotNull(type1);
    assertEquals("TypeVar('T')", type1.getName());
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

  public void testPep484FunctionTypeInSingleLineComment() {
    myFixture.configureByFile("typeParser/typeParser.py");
    final PyType type = PyTypeParser.parsePep484FunctionTypeComment(myFixture.getFile(), "(bool, MyObject, *str, **int) -> int").getType();
    assertInstanceOf(type, PyCallableType.class);
    final PyCallableType callableType = (PyCallableType)type;
    assertNotNull(callableType);
    final TypeEvalContext context = getTypeEvalContext();
    final PyType returnType = callableType.getReturnType(context);
    assertNotNull(returnType);
    assertEquals("int", returnType.getName());
    final List<PyCallableParameter> parameterTypes = callableType.getParameters(context);
    assertNotNull(parameterTypes);
    assertSize(4, parameterTypes);
    final PyType type1 = parameterTypes.get(0).getType(context);
    assertNotNull(type1);
    assertEquals("bool", type1.getName());
    
    final PyType type2 = parameterTypes.get(1).getType(context);
    assertNotNull(type2);
    assertEquals("MyObject", type2.getName());
    
    final PyClassType type3 = as(parameterTypes.get(2).getType(context), PyClassType.class);
    assertNotNull(type3);
    assertEquals("(str, ...)", type3.getName());
    
    final PyCollectionType type4 = as(parameterTypes.get(3).getType(context), PyCollectionType.class);
    assertNotNull(type4);
    assertEquals("dict", type4.getName());
    assertSize(2,  type4.getElementTypes(context));
    assertEquals("str", type4.getElementTypes(context).get(0).getName());
    assertEquals("int", type4.getElementTypes(context).get(1).getName());
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
