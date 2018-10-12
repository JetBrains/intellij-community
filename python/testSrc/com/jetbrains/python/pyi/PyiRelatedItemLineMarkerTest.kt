// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.pyi

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFunction

class PyiRelatedItemLineMarkerTest : PyTestCase() {

  fun testOverloadsAndImplementationInPyFile() {
    assertNoMarker("baz", false)
  }

  // runtime -> stub

  fun testNoSimilarClassForRuntimeMethod() {
    assertNoMarker("method", false)
  }

  fun testNoSimilarForRuntimeMethod() {
    assertNoMarker("method", false)
  }

  fun testSimilarForRuntimeMethod() {
    assertMarker("method", false)
  }

  fun testAnotherTypeSimilarForRuntimeMethod() {
    assertMarker("method", false)
  }

  fun testNoSimilarModuleForRuntimeFunction() {
    assertNoMarker("function", false)
  }

  fun testNoSimilarForRuntimeFunction() {
    assertNoMarker("function", false)
  }

  fun testSimilarForRuntimeFunction() {
    assertMarker("function", false)
  }

  fun testAnotherTypeSimilarForRuntimeFunction() {
    assertMarker("function", false)
  }

  // stub -> runtime

  fun testNoSimilarClassForMethodStub() {
    assertNoMarker("bar", true)
  }

  fun testNoSimilarForMethodStub() {
    assertNoMarker("bar", true)
  }

  fun testSimilarForMethodStub() {
    assertMarker("bar", true)
  }

  fun testAnotherTypeSimilarForMethodStub() {
    assertMarker("bar", true)
  }

  fun testNoSimilarModuleForFunctionStub() {
    assertNoMarker("foo", true)
  }

  fun testNoSimilarForFunctionStub() {
    assertNoMarker("foo", true)
  }

  fun testSimilarForFunctionStub() {
    assertMarker("foo", true)
  }

  fun testAnotherTypeSimilarForFunctionStub() {
    assertMarker("foo", true)
  }

  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/pyi/lineMarkers"
  }

  private fun assertMarker(elementName: String, pyi: Boolean) {
    val expectedTooltip = if (pyi) "Stub for item in b.py" else "Has stub item in a.pyi"
    assertEquals(expectedTooltip, getMarkers(elementName, pyi).singleOrNull()?.lineMarkerTooltip)
  }

  private fun assertNoMarker(elementName: String, pyi: Boolean) {
    assertEmpty(getMarkers(elementName, pyi))
  }

  private fun getMarkers(elementName: String, pyi: Boolean): List<LineMarkerInfo<PsiElement>> {
    myFixture.copyDirectoryToProject(getTestName(false), "")
    myFixture.configureByFile(if (pyi) "b.pyi" else "a.py")

    val functions = PsiTreeUtil.findChildrenOfType(myFixture.file, PyFunction::class.java)
    functions.forEach { assertEquals(elementName, it.name) }
    assertNotEmpty(functions)

    myFixture.doHighlighting()

    return DaemonCodeAnalyzerImpl
      .getLineMarkers(myFixture.editor.document, myFixture.project)
      .filter { PsiTreeUtil.getParentOfType(it.element, PsiNameIdentifierOwner::class.java)?.name == elementName }
  }
}