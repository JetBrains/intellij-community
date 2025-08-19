// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.lang.LanguageASTFactory
import com.intellij.testFramework.ParsingTestCase
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

  override fun getTestDataPath(): String {
    return PythonTestUtil.getTestDataPath()
  }
}
