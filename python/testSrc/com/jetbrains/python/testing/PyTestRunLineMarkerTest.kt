// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.fixtures.PyTestCase
import junit.framework.TestCase

open class PyTestRunLineMarkerTest : PyTestCase() {
  companion object {
    const val TESTS_DIR = "/pyTestLineMarker/"
    const val PYTHON_FILE = "pythonFile.py"
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + TESTS_DIR

  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
    myFixture.copyDirectoryToProject("", "")
  }

  protected fun getCaretElement(fileName: String): PsiElement? {
    val psiFile = configureByFile(fileName)
    TestCase.assertNotNull("Can't find test file", psiFile)
    val element = psiFile?.findElementAt(myFixture.caretOffset)
    TestCase.assertNotNull("Can't find caret element", element)
    return element
  }

  protected open fun configureByFile(fileName: String): PsiFile? = myFixture.configureByFile(fileName)

  private fun getInfo(element: PsiElement, lineMarkerContributor: RunLineMarkerContributor): Info? = lineMarkerContributor.getInfo(element)

  protected fun assertInfoFound(element: PsiElement, lineMarkerContributor: RunLineMarkerContributor) {
    val info = getInfo(element, lineMarkerContributor)
    TestCase.assertNotNull("Info is not found", info)
    if (info != null) {
      TestCase.assertNotNull("Run icon is not found", info.icon)
    }
  }

  protected fun assertInfoNotFound(element: PsiElement, lineMarkerContributor: RunLineMarkerContributor) {
    TestCase.assertNull("Info is found", getInfo(element, lineMarkerContributor))
  }

  fun testPythonFile() {
    val lineMarkerContributor = PyTestLineMarkerContributor()
    val element = getCaretElement(PYTHON_FILE)
    if (element != null) {
      assertInfoFound(element, lineMarkerContributor)
    }
  }
}