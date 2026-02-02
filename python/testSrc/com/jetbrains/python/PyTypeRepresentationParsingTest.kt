// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.lang.LanguageASTFactory
import com.intellij.testFramework.ParsingTestCase
import com.jetbrains.python.ast.PyAstTypeParameter
import com.jetbrains.python.codeInsight.typeRepresentation.PyTypeRepresentationDialect
import com.jetbrains.python.codeInsight.typeRepresentation.PyTypeRepresentationParserDefinition
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyFunctionTypeRepresentation
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile
import com.jetbrains.python.documentation.doctest.PyDocstringTokenSetContributor
import com.jetbrains.python.psi.PyEllipsisLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PythonVisitorFilter
import com.jetbrains.python.psi.impl.PythonASTFactory
import junit.framework.TestCase
import java.io.IOException
import org.junit.jupiter.api.assertInstanceOf as assertInstanceOfJunit5

class PyTypeRepresentationParsingTest : ParsingTestCase("typeRepresentation/parsing", "pythonTypeRepresentation",
                                                        PyTypeRepresentationParserDefinition(), PythonParserDefinition()) {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    application.registerService(PyElementTypesFacade::class.java, PyElementTypesFacadeImpl::class.java)
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME,
                           PythonDialectsTokenSetContributor::class.java)
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, PythonTokenSetContributor())
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, PyDocstringTokenSetContributor())
    addExplicitExtension(LanguageASTFactory.INSTANCE, PythonLanguage.getInstance(), PythonASTFactory())
  }

  @Throws(Exception::class)
  override fun tearDown() {
    // clear cached extensions
    try {
      PythonVisitorFilter.INSTANCE.removeExplicitExtension(PythonLanguage.INSTANCE,
                                                           PythonVisitorFilter { _, _ -> false })
      PythonVisitorFilter.INSTANCE.removeExplicitExtension(PyTypeRepresentationDialect,
                                                           PythonVisitorFilter { _, _ -> false })
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun doCodeTest(code: String) {
    try {
      super.doCodeTest(code)
    }
    catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  fun parseCallable(code: String): PyFunctionTypeRepresentation {
    doCodeTest(code)
    return assertInstanceOfJunit5<PyFunctionTypeRepresentation>(parsedType)
  }

  private val parsedType: PyExpression? get() = assertInstanceOfJunit5<PyTypeRepresentationFile>(myFile).type

  fun `test todo`() {
    doCodeTest("@Todo(`Unpack[]` special form)")
  }

  fun `test todo in callable`() {
    val callable = parseCallable("(@Todo(`Unpack[]` special form)) -> None")
    assertNotNull(callable)
    assertSize(1, callable.parameterList.parameters)
  }

  fun `test callable empty`() {
    val callable = parseCallable("() -> None")
    assertEmpty(callable.parameterList.parameters)
    val returnType = callable.returnType
    assertNotNull(returnType)
    assertEquals("None", returnType!!.text)
  }

  fun `test callable simple`() {
    parseCallable("(int, str) -> None")
  }

  fun `test callable named parameter`() {
    parseCallable("(a: int) -> None")
  }

  fun `test callable default parameter`() {
    parseCallable("(a: int = ...) -> None")
  }

  fun `test callable varargs`() {
    parseCallable("(*int, **str) -> None")
  }

  fun `test callable complex varargs`() {
    parseCallable("(*a: *tuple[int, str], **b: **B) -> None")
  }

  fun `test callable ellipsis`() {
    val callable = parseCallable("(...) -> None")
    val paramTypes = callable.parameterList.parameters
    assertSize(1, paramTypes)
    assertInstanceOfJunit5<PyEllipsisLiteralExpression>(paramTypes[0])
    val returnType = callable.returnType
    assertNotNull(returnType)
  }

  fun `test callable nested`() {
    val callable = parseCallable("(int, (str) -> None, int) -> (str) -> None")
    val paramTypes = callable.parameterList.parameters
    assertSize(3, paramTypes)
    val nestedCallable = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(paramTypes[1])
    assertSize(1, nestedCallable.parameterList.parameters)
    val nestedReturnType = nestedCallable.returnType
    assertNotNull(nestedReturnType)
    TestCase.assertEquals(
      "None", nestedReturnType!!.text
    )

    val returnType = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(paramTypes[1])
    assertSize(1, returnType.parameterList.parameters)
    val returnTypeNestedReturnType = returnType.returnType
    assertNotNull(returnTypeNestedReturnType)
    TestCase.assertEquals(
      "None", nestedReturnType.text
    )
  }

  fun `test callable no return type`() {
    val callable = parseCallable("(int) -> ")
    val paramTypes = callable.parameterList.parameters
    assertSize(1, paramTypes)
    assertNull(callable.returnType)
  }

  fun `test callable dangling comma`() {
    parseCallable("(int,) -> None")
  }

  fun `test callable empty function type`() {
    doCodeTest("")
    assertNull(parsedType)
  }

  fun `test callable no type after star`() {
    parseCallable("(*) -> int")
  }

  // Tests for function types (def syntax) added in PY-86754
  fun `test function type simple`() {
    val callable = parseCallable("def test.f() -> str")
    assertNotNull(callable.functionName)
    assertEquals("test.f", callable.functionName!!.toString())
    assertEmpty(callable.parameterList.parameters)
    val returnType = callable.returnType
    assertNotNull(returnType)
    assertEquals("str", returnType!!.text)
  }

  fun `test function type with parameters`() {
    val callable = parseCallable("def test.f(a: int, b: str) -> bool")
    assertNotNull(callable.functionName)
    assertEquals("test.f", callable.functionName!!.toString())
    assertSize(2, callable.parameterList.parameters)
    val returnType = callable.returnType
    assertNotNull(returnType)
    assertEquals("bool", returnType!!.text)
  }

  fun `test function type with default values`() {
    val callable = parseCallable("def test.f(a: int, b: str = ...) -> None")
    assertNotNull(callable.functionName)
    assertSize(2, callable.parameterList.parameters)
  }

  fun `test function type with positional only separator`() {
    val callable = parseCallable("def test.f(a: int, /, b: str) -> None")
    assertNotNull(callable.functionName)
    assertSize(3, callable.parameterList.parameters) // a, /, b
  }

  fun `test function type with varargs`() {
    val callable = parseCallable("def test.f(*args: int) -> None")
    assertNotNull(callable.functionName)
    assertSize(1, callable.parameterList.parameters)
  }

  fun `test function type with kwargs`() {
    val callable = parseCallable("def test.f(**kwargs: str) -> None")
    assertNotNull(callable.functionName)
    assertSize(1, callable.parameterList.parameters)
  }

  fun `test function type with complex varargs`() {
    val callable = parseCallable("def test.f(*args: *tuple[int], **kwargs: **Dict) -> None")
    assertNotNull(callable.functionName)
    assertSize(2, callable.parameterList.parameters)
  }

  fun `test function type nested class`() {
    val callable = parseCallable("def test.A.B.f() -> None")
    assertNotNull(callable.functionName)
    assertEquals("test.A.B.f", callable.functionName!!.toString())
  }

  fun `test function type with nested callable in parameter`() {
    val callable = parseCallable("def test.f(callback: (int) -> str) -> None")
    assertNotNull(callable.functionName)
    assertSize(1, callable.parameterList.parameters)
  }

  fun `test function type with nested callable in return`() {
    val callable = parseCallable("def test.f() -> (int) -> str")
    assertNotNull(callable.functionName)
    assertEmpty(callable.parameterList.parameters)
    val returnType = callable.returnType
    assertNotNull(returnType)
    assertInstanceOfJunit5<PyFunctionTypeRepresentation>(returnType!!)
  }

  fun `test function type with all parameter types`() {
    val callable = parseCallable("def test.f(pos: int, /, normal: str, *args: float, kw: bool, **kwargs: dict) -> None")
    assertNotNull(callable.functionName)
    assertSize(6, callable.parameterList.parameters) // pos, /, normal, args, kw, kwargs
  }

  // Tests for type parameter lists added in PY-86755
  fun `test type parameter list simple`() {
    val callable = parseCallable("[T](x: T) -> T")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("T", typeParams[0].name)
    assertNull(typeParams[0].boundExpression)
  }

  fun `test type parameter list with bound`() {
    val callable = parseCallable("[T: int](x: T) -> T")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("T", typeParams[0].name)
    assertNotNull(typeParams[0].boundExpression)
    assertEquals("int", typeParams[0].boundExpression!!.text)
  }

  fun `test type parameter list with qualified bound`() {
    val callable = parseCallable("[T: builtins.int](x: T) -> T")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("T", typeParams[0].name)
    assertNotNull(typeParams[0].boundExpression)
    assertEquals("builtins.int", typeParams[0].boundExpression!!.text)
  }

  fun `test type parameter list multiple params`() {
    val callable = parseCallable("[T, U, V](x: T, y: U) -> V")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(3, typeParams)
    assertEquals("T", typeParams[0].name)
    assertEquals("U", typeParams[1].name)
    assertEquals("V", typeParams[2].name)
  }

  fun `test type parameter list with mixed bounds`() {
    val callable = parseCallable("[T: int, U, V: str](x: T, y: U) -> V")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(3, typeParams)
    assertEquals("T", typeParams[0].name)
    assertNotNull(typeParams[0].boundExpression)
    assertEquals("U", typeParams[1].name)
    assertNull(typeParams[1].boundExpression)
    assertEquals("V", typeParams[2].name)
    assertNotNull(typeParams[2].boundExpression)
  }

  fun `test type parameter list star`() {
    val callable = parseCallable("[*Ts](x: *Ts) -> None")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("Ts", typeParams[0].name)
    assertEquals(PyAstTypeParameter.Kind.TypeVarTuple, typeParams[0].kind)
  }

  fun `test type parameter list double star`() {
    val callable = parseCallable("[**P](x: int) -> None")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("P", typeParams[0].name)
    assertEquals(PyAstTypeParameter.Kind.ParamSpec, typeParams[0].kind)
  }

  fun `test type parameter list mixed kinds`() {
    val callable = parseCallable("[T, *Ts, **P](x: T) -> None")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(3, typeParams)
    assertEquals("T", typeParams[0].name)
    assertEquals(PyAstTypeParameter.Kind.TypeVar, typeParams[0].kind)
    assertEquals("Ts", typeParams[1].name)
    assertEquals(PyAstTypeParameter.Kind.TypeVarTuple, typeParams[1].kind)
    assertEquals("P", typeParams[2].name)
    assertEquals(PyAstTypeParameter.Kind.ParamSpec, typeParams[2].kind)
  }

  fun `test function type with type parameter list`() {
    val callable = parseCallable("def test.f[T](x: T) -> T")
    assertNotNull(callable.functionName)
    assertEquals("test.f", callable.functionName!!.toString())
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("T", typeParams[0].name)
  }

  fun `test callable without type parameter list`() {
    val callable = parseCallable("(x: int) -> str")
    assertNull(callable.functionName)
    assertNull(callable.typeParameterList)
  }

  fun `test type parameter list with default`() {
    val callable = parseCallable("[T = int](x: T) -> T")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("T", typeParams[0].name)
    assertNotNull(typeParams[0].defaultExpression)
    assertEquals("int", typeParams[0].defaultExpression!!.text)
  }

  fun `test type parameter list with bound and default`() {
    val callable = parseCallable("[T: str = int](x: T) -> T")
    val typeParamList = callable.typeParameterList
    assertNotNull(typeParamList)
    val typeParams = typeParamList!!.typeParameters
    assertSize(1, typeParams)
    assertEquals("T", typeParams[0].name)
    assertNotNull(typeParams[0].boundExpression)
    assertEquals("str", typeParams[0].boundExpression!!.text)
    assertNotNull(typeParams[0].defaultExpression)
    assertEquals("int", typeParams[0].defaultExpression!!.text)
  }

  override fun getTestDataPath(): String {
    return PythonTestUtil.getTestDataPath()
  }
}
