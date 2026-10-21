// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.openapi.application.ReadAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.ast.PyAstTypeParameter
import com.jetbrains.python.codeInsight.typeRepresentation.PyTypeRepresentationParserDefinition
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyFunctionTypeRepresentation
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyEllipsisLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf as assertInstanceOfJunit5

@Subsystems.CodeInsight
@Layers.Functional
@TestApplication
class PyTypeRepresentationParsingTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val parsing by parsingFixture(projectFixture, PythonTestUtil.getTestDataPath() + "/typeRepresentation/parsing",
                                        "pythonTypeRepresentation", false,
                                        PyTypeRepresentationParserDefinition(), PythonParserDefinition())

  private fun doCodeTest(code: String) {
    parsing.doCodeTest(code)
  }

  fun parseCallable(code: String): PyFunctionTypeRepresentation {
    doCodeTest(code)
    return read { assertInstanceOfJunit5<PyFunctionTypeRepresentation>(parsedType) }
  }

  private val parsedType: PyExpression? get() = assertInstanceOfJunit5<PyTypeRepresentationFile>(parsing.file).type

  private fun <T> read(block: () -> T): T = ReadAction.computeBlocking<T, RuntimeException> { block() }

  @Test
  fun `test todo`() {
    doCodeTest("@Todo(`Unpack[]` special form)")
  }

  @Test
  fun `test todo in callable`() {
    val callable = parseCallable("(@Todo(`Unpack[]` special form)) -> None")
    read {
      assertNotNull(callable)
      assertEquals(1, callable.parameterList.parameters.size)
    }
  }

  @Test
  fun `test callable empty`() {
    val callable = parseCallable("() -> None")
    read {
      assertTrue(callable.parameterList.parameters.isEmpty())
      val returnType = callable.returnType
      assertNotNull(returnType)
      assertEquals("None", returnType!!.text)
    }
  }

  @Test
  fun `test callable simple`() {
    parseCallable("(int, str) -> None")
  }

  @Test
  fun `test callable named parameter`() {
    parseCallable("(a: int) -> None")
  }

  @Test
  fun `test callable default parameter`() {
    parseCallable("(a: int = ...) -> None")
  }

  @Test
  fun `test callable varargs`() {
    parseCallable("(*int, **str) -> None")
  }

  @Test
  fun `test callable complex varargs`() {
    parseCallable("(*a: *tuple[int, str], **b: **B) -> None")
  }

  @Test
  fun `test callable ellipsis`() {
    val callable = parseCallable("(...) -> None")
    read {
      val paramTypes = callable.parameterList.parameters
      assertEquals(1, paramTypes.size)
      assertInstanceOfJunit5<PyEllipsisLiteralExpression>(paramTypes[0])
      val returnType = callable.returnType
      assertNotNull(returnType)
    }
  }

  @Test
  fun `test callable nested`() {
    val callable = parseCallable("(int, (str) -> None, int) -> (str) -> None")
    read {
      val paramTypes = callable.parameterList.parameters
      assertEquals(3, paramTypes.size)
      val nestedCallable = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(paramTypes[1])
      assertEquals(1, nestedCallable.parameterList.parameters.size)
      val nestedReturnType = nestedCallable.returnType
      assertNotNull(nestedReturnType)
      assertEquals(
        "None", nestedReturnType!!.text
      )

      val returnType = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(paramTypes[1])
      assertEquals(1, returnType.parameterList.parameters.size)
      val returnTypeNestedReturnType = returnType.returnType
      assertNotNull(returnTypeNestedReturnType)
      assertEquals(
        "None", nestedReturnType.text
      )
    }
  }

  @Test
  fun `test callable no return type`() {
    val callable = parseCallable("(int) -> ")
    read {
      val paramTypes = callable.parameterList.parameters
      assertEquals(1, paramTypes.size)
      assertNull(callable.returnType)
    }
  }

  @Test
  fun `test callable dangling comma`() {
    parseCallable("(int,) -> None")
  }

  @Test
  fun `test callable empty function type`() {
    doCodeTest("")
    read {
      assertNull(parsedType)
    }
  }

  @Test
  fun `test callable no type after star`() {
    parseCallable("(*) -> int")
  }

  // Tests for function types (def syntax) added in PY-86754
  @Test
  fun `test function type simple`() {
    val callable = parseCallable("def test.f() -> str")
    read {
      assertNotNull(callable.functionName)
      assertEquals("test.f", callable.functionName!!.toString())
      assertTrue(callable.parameterList.parameters.isEmpty())
      val returnType = callable.returnType
      assertNotNull(returnType)
      assertEquals("str", returnType!!.text)
    }
  }

  @Test
  fun `test function type with parameters`() {
    val callable = parseCallable("def test.f(a: int, b: str) -> bool")
    read {
      assertNotNull(callable.functionName)
      assertEquals("test.f", callable.functionName!!.toString())
      assertEquals(2, callable.parameterList.parameters.size)
      val returnType = callable.returnType
      assertNotNull(returnType)
      assertEquals("bool", returnType!!.text)
    }
  }

  @Test
  fun `test function type with default values`() {
    val callable = parseCallable("def test.f(a: int, b: str = ...) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(2, callable.parameterList.parameters.size)
    }
  }

  @Test
  fun `test function type with positional only separator`() {
    val callable = parseCallable("def test.f(a: int, /, b: str) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(3, callable.parameterList.parameters.size) // a, /, b
    }
  }

  @Test
  fun `test function type with varargs`() {
    val callable = parseCallable("def test.f(*args: int) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(1, callable.parameterList.parameters.size)
    }
  }

  @Test
  fun `test function type with kwargs`() {
    val callable = parseCallable("def test.f(**kwargs: str) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(1, callable.parameterList.parameters.size)
    }
  }

  @Test
  fun `test function type with complex varargs`() {
    val callable = parseCallable("def test.f(*args: *tuple[int], **kwargs: **Dict) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(2, callable.parameterList.parameters.size)
    }
  }

  @Test
  fun `test function type nested class`() {
    val callable = parseCallable("def test.A.B.f() -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals("test.A.B.f", callable.functionName!!.toString())
    }
  }

  @Test
  fun `test function type with nested callable in parameter`() {
    val callable = parseCallable("def test.f(callback: (int) -> str) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(1, callable.parameterList.parameters.size)
    }
  }

  @Test
  fun `test function type with nested callable in return`() {
    val callable = parseCallable("def test.f() -> (int) -> str")
    read {
      assertNotNull(callable.functionName)
      assertTrue(callable.parameterList.parameters.isEmpty())
      val returnType = callable.returnType
      assertNotNull(returnType)
      assertInstanceOfJunit5<PyFunctionTypeRepresentation>(returnType!!)
    }
  }

  @Test
  fun `test function type with all parameter types`() {
    val callable = parseCallable("def test.f(pos: int, /, normal: str, *args: float, kw: bool, **kwargs: dict) -> None")
    read {
      assertNotNull(callable.functionName)
      assertEquals(6, callable.parameterList.parameters.size) // pos, /, normal, args, kw, kwargs
    }
  }

  // Tests for type parameter lists added in PY-86755
  @Test
  fun `test type parameter list simple`() {
    val callable = parseCallable("[T](x: T) -> T")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertNull(typeParams[0].boundExpression)
    }
  }

  @Test
  fun `test type parameter list with bound`() {
    val callable = parseCallable("[T: int](x: T) -> T")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertNotNull(typeParams[0].boundExpression)
      assertEquals("int", typeParams[0].boundExpression!!.text)
    }
  }

  @Test
  fun `test type parameter list with qualified bound`() {
    val callable = parseCallable("[T: builtins.int](x: T) -> T")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertNotNull(typeParams[0].boundExpression)
      assertEquals("builtins.int", typeParams[0].boundExpression!!.text)
    }
  }

  @Test
  fun `test type parameter list multiple params`() {
    val callable = parseCallable("[T, U, V](x: T, y: U) -> V")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(3, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertEquals("U", typeParams[1].name)
      assertEquals("V", typeParams[2].name)
    }
  }

  @Test
  fun `test type parameter list with mixed bounds`() {
    val callable = parseCallable("[T: int, U, V: str](x: T, y: U) -> V")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(3, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertNotNull(typeParams[0].boundExpression)
      assertEquals("U", typeParams[1].name)
      assertNull(typeParams[1].boundExpression)
      assertEquals("V", typeParams[2].name)
      assertNotNull(typeParams[2].boundExpression)
    }
  }

  @Test
  fun `test type parameter list star`() {
    val callable = parseCallable("[*Ts](x: *Ts) -> None")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("Ts", typeParams[0].name)
      assertEquals(PyAstTypeParameter.Kind.TypeVarTuple, typeParams[0].kind)
    }
  }

  @Test
  fun `test type parameter list double star`() {
    val callable = parseCallable("[**P](x: int) -> None")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("P", typeParams[0].name)
      assertEquals(PyAstTypeParameter.Kind.ParamSpec, typeParams[0].kind)
    }
  }

  @Test
  fun `test type parameter list mixed kinds`() {
    val callable = parseCallable("[T, *Ts, **P](x: T) -> None")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(3, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertEquals(PyAstTypeParameter.Kind.TypeVar, typeParams[0].kind)
      assertEquals("Ts", typeParams[1].name)
      assertEquals(PyAstTypeParameter.Kind.TypeVarTuple, typeParams[1].kind)
      assertEquals("P", typeParams[2].name)
      assertEquals(PyAstTypeParameter.Kind.ParamSpec, typeParams[2].kind)
    }
  }

  @Test
  fun `test function type with type parameter list`() {
    val callable = parseCallable("def test.f[T](x: T) -> T")
    read {
      assertNotNull(callable.functionName)
      assertEquals("test.f", callable.functionName!!.toString())
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("T", typeParams[0].name)
    }
  }

  @Test
  fun `test callable without type parameter list`() {
    val callable = parseCallable("(x: int) -> str")
    read {
      assertNull(callable.functionName)
      assertNull(callable.typeParameterList)
    }
  }

  @Test
  fun `test type parameter list with default`() {
    val callable = parseCallable("[T = int](x: T) -> T")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      assertEquals("T", typeParams[0].name)
      assertNotNull(typeParams[0].defaultExpression)
      assertEquals("int", typeParams[0].defaultExpression!!.text)
    }
  }

  @Test
  fun `test type parameter list with bound and default`() {
    val callable = parseCallable("[T: str = int](x: T) -> T")
    read {
      val typeParamList = callable.typeParameterList
      assertNotNull(typeParamList)
      val typeParams = typeParamList!!.typeParameters
      assertEquals(1, typeParams.size)
      val t = typeParams.single()
      assertEquals("T", t.name)
      assertNotNull(t.boundExpression)
      assertEquals("str", t.boundExpression!!.text)
      assertNotNull(t.defaultExpression)
      assertEquals("int", t.defaultExpression!!.text)
    }
  }

  @Test
  fun `test callable in subscription`() {
    doCodeTest("x[(int) -> str]")
    read {
      val expr = assertInstanceOfJunit5<PySubscriptionExpression>(parsedType)
      val callable = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(expr.indexExpression)
      assertEquals(1, callable.parameterList.parameters.size)
      assertEquals("int", callable.parameterList.parameters.single().text)
      assertEquals("str", callable.returnType!!.text)
    }
  }

  @Test
  fun `test callable in subscription as tuple`() {
    doCodeTest("x[(int) -> int, (str) -> str]")
    read {
      val expr = assertInstanceOfJunit5<PySubscriptionExpression>(parsedType)
      val tuple = assertInstanceOfJunit5<PyTupleExpression>(expr.indexExpression)
      assertEquals(2, tuple.elements.size)
      val first = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(tuple.elements[0])
      assertEquals(1, first.parameterList.parameters.size)
      assertEquals("int", first.parameterList.parameters.single().text)
      assertEquals("int", first.returnType!!.text)
      val second = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(tuple.elements[1])
      assertEquals(1, second.parameterList.parameters.size)
      assertEquals("str", second.parameterList.parameters.single().text)
      assertEquals("str", second.returnType!!.text)
    }
  }

  @Test
  fun `test callable in union`() {
    doCodeTest("((int) -> int) | (str) -> str")
    read {
      val expr = assertInstanceOfJunit5<PyBinaryExpression>(parsedType)
      val left = assertInstanceOfJunit5<PyFunctionTypeRepresentation>((expr.leftExpression as PyParenthesizedExpression).containedExpression)
      assertEquals(1, left.parameterList.parameters.size)
      assertEquals("int", left.parameterList.parameters.single().text)
      assertEquals("int", left.returnType!!.text)
      val right = assertInstanceOfJunit5<PyFunctionTypeRepresentation>(expr.rightExpression)
      assertEquals(1, right.parameterList.parameters.size)
      assertEquals("str", right.parameterList.parameters.single().text)
      assertEquals("str", right.returnType!!.text)
    }
  }
}
