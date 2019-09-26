// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyGotoDeclarationHandler
import com.jetbrains.python.pyi.PyiFile

class PyNavigationTest : PyTestCase() {

  // PY-35129
  fun testGoToDeclarationOnPyiFile() {
    configureByDir("onPyiFile")
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    checkPyNotPyi(target)
  }

  // PY-35129
  fun testGoToImplementationOnPyiFile() {
    configureByDir("onPyiFile")
    val gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
    assertSize(1, gotoData.targets)
    checkPyNotPyi(gotoData.targets[0])
  }

  // PY-35129
  fun testGoToDeclarationForDirectory() {
    runWithAdditionalFileInLibDir("collections/__init__.py", "") {
      configureByDir(getTestName(true))
      val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
      checkPyNotPyi(target)
    }
  }

  private fun configureByDir(dirName: String) {
    myFixture.copyDirectoryToProject(dirName, "")
    myFixture.configureByFile("test.py")
    assertTrue(myFixture.elementAtCaret is PyiFile)
  }

  private fun checkPyNotPyi(file: PsiElement?) {
    assertTrue(file is PyFile)
    assertTrue(file !is PyiFile)
  }

  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/navigation"
  }

  override fun getProjectDescriptor(): LightProjectDescriptor? = ourPy3Descriptor


}