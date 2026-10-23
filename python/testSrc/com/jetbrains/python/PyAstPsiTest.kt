// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.ast.PyAstElsePart
import com.jetbrains.python.ast.PyAstFromImportStatement
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.ast.PyAstIfPartElif
import com.jetbrains.python.ast.PyAstIfStatement
import com.jetbrains.python.ast.PyAstImportStatement
import com.jetbrains.python.ast.PyAstImportStatementBase
import com.jetbrains.python.ast.PyAstNamedParameter
import com.jetbrains.python.ast.impl.PyAstFileImpl
import com.jetbrains.python.ast.impl.PyAstPsiElementFactory
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile

/**
 * The AST-only PSI of [PyAstPsiElementFactory], which the light mode of JetBrains Light and the JetBrains Client frontend use.
 * The test masks the stub-based factory, so the parser definition builds the AST-only tree.
 */
@Subsystems.CodeInsight
@Components.Parsing
@Layers.Functional
class PyAstPsiTest : PyTestCase() {
  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(PyPsiElementFactory.EP_NAME, listOf(PyAstPsiElementFactory()), testRootDisposable)
    myFixture.configureByText(PythonFileType.INSTANCE, SOURCE)
  }

  fun testTheFileIsAstOnly() {
    assertInstanceOf(myFixture.file, PyAstFileImpl::class.java)
    assertFalse(myFixture.file is PyFile)
  }

  fun testImportStatementsGetTheirOwnInterfaces() {
    val imports = PsiTreeUtil.findChildrenOfType(myFixture.file, PyAstImportStatementBase::class.java).toList()
    assertEquals(2, imports.size)
    assertInstanceOf(imports[0], PyAstImportStatement::class.java)
    assertFalse(imports[0] is PyAstFromImportStatement)
    assertEquals(listOf("os"), imports[0].fullyQualifiedObjectNames)
    assertInstanceOf(imports[1], PyAstFromImportStatement::class.java)
  }

  fun testTheElifPartIsAnIfPart() {
    val ifStatement = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, PyAstIfStatement::class.java))
    val elifPart = ifStatement.elifParts.single()
    assertInstanceOf(elifPart, PyAstIfPartElif::class.java)
    assertTrue(elifPart.isElif)
    assertFalse(elifPart is PyAstElsePart)
  }

  fun testADecoratorListHoldsItsDecorators() {
    val (method, function) = PsiTreeUtil.findChildrenOfType(myFixture.file, PyAstFunction::class.java).toList()
    assertNull(method.decoratorList)
    assertEquals(listOf("dec"), requireNotNull(function.decoratorList).decorators.map { it.name })
  }

  fun testAMethodKnowsItsClassAndItsTypeParameters() {
    val (method, function) = PsiTreeUtil.findChildrenOfType(myFixture.file, PyAstFunction::class.java).toList()
    assertEquals("m", method.name)
    assertEquals("C", method.containingClass?.name)
    assertNotNull(method.asMethod())
    assertEquals(listOf("T"), requireNotNull(method.typeParameterList).typeParameters.map { it.name })
    assertEquals("f", function.name)
    assertNull(function.containingClass)
    assertNull(function.asMethod())
    assertNull(function.typeParameterList)
  }

  fun testTheAnnotationFollowsAnEdit() {
    val parameter = parameterA()
    assertEquals("int", parameter.annotation?.value?.text)
    val document = myFixture.editor.document
    val offset = document.text.indexOf(ANNOTATION)
    WriteCommandAction.runWriteCommandAction(myFixture.project) { document.deleteString(offset, offset + ANNOTATION.length) }
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
    // The reparse keeps the parameter element when it can. Then a cached annotation would be stale.
    val current = if (parameter.isValid) parameter else parameterA()
    assertNull(current.annotation)
  }

  private fun parameterA(): PyAstNamedParameter =
    PsiTreeUtil.findChildrenOfType(myFixture.file, PyAstNamedParameter::class.java).first { it.name == "a" }

  private companion object {
    const val ANNOTATION = ": int"

    val SOURCE = """
      import os
      from os import path

      class C:
          def m[T](self, a$ANNOTATION) -> None:
              pass

      @dec
      def f():
          pass

      if path:
          pass
      elif os:
          pass
    """.trimIndent()
  }
}
