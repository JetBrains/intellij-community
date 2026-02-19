package com.jetbrains.python.testing

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.fixtures.PyTestCase

class PyTestRunLineMarkerRespectFilePatternTest : PyTestCase() {
  companion object {
    private const val TESTS_DIR = "/pyTestLineMarker/"
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + TESTS_DIR

  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
    myFixture.copyDirectoryToProject("", "")
  }

  private fun configureByFile(fileName: String): PsiFile? = myFixture.configureByFile(fileName)

  private fun getCaretElement(fileName: String): PsiElement? {
    val psiFile = configureByFile(fileName)
    assertNotNull("Can't find test file", psiFile)
    val element = psiFile?.findElementAt(myFixture.caretOffset)
    assertNotNull("Can't find caret element", element)
    return element
  }

  private fun getInfo(element: PsiElement, lineMarkerContributor: RunLineMarkerContributor): Info? = lineMarkerContributor.getInfo(element)

  private fun assertInfoNotFound(element: PsiElement, lineMarkerContributor: RunLineMarkerContributor) {
    assertNull("Info is found", getInfo(element, lineMarkerContributor))
  }

  fun testNoGutterForNonTestModule() {
    val lineMarkerContributor = PyTestLineMarkerContributor()
    val element = getCaretElement("module.py")
    if (element != null) {
      assertInfoNotFound(element, lineMarkerContributor)
    }
  }
}