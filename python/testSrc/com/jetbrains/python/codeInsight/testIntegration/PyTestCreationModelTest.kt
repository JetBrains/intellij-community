// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.testing.PyTestFrameworkService
import com.jetbrains.python.testing.PythonTestConfigurationsModel
import com.jetbrains.python.testing.TestRunnerService
import org.junit.Assert

class PyTestCreationModelTest : PyTestCase() {
  private val dir get() = myFixture.file.containingDirectory.virtualFile
  private val dirPath get() = dir.path
  private val service: TestRunnerService get() = TestRunnerService.getInstance(myFixture.module)
  private val testsFolderName = "tests"

  fun testWithUnitTest() {
    service.projectConfiguration = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME
    val modelToTestClass = getModel(true)!!
    Assert.assertEquals("test_create_tst.py", modelToTestClass.fileName)
    Assert.assertEquals("TestSpam", modelToTestClass.className)
    Assert.assertEquals(dirPath, modelToTestClass.targetDir)
    Assert.assertEquals(modelToTestClass.methods, listOf("test_eggs", "test_eggs_and_ham"))

    val modelToTestFunction = getModel(false)!!
    Assert.assertEquals("test_create_tst.py", modelToTestFunction.fileName)
    Assert.assertEquals("Test", modelToTestFunction.className)
    Assert.assertEquals(dirPath, modelToTestClass.targetDir)
    Assert.assertEquals(modelToTestFunction.methods, listOf("test_test_foo"))
  }

  fun testWithPyTest() {
    service.projectConfiguration = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)
    val modelToTestClass = getModel(true)!!
    Assert.assertEquals("test_create_tst.py", modelToTestClass.fileName)
    Assert.assertEquals("", modelToTestClass.className)
    Assert.assertEquals(dirPath, modelToTestClass.targetDir)
    Assert.assertEquals(modelToTestClass.methods, listOf("test_eggs", "test_eggs_and_ham"))

    Assert.assertNull("test_foo is test from pytest point of view, can't test it", getModel(false))
  }

  fun testTestFolderDetected() {
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.runAndWait<Throwable> {
        VfsUtil.createDirectoryIfMissing(dir, testsFolderName)
      }
    }
    val modelToTestClass = getModel(true)!!
    Assert.assertEquals(dir.findChild(testsFolderName)!!.path, modelToTestClass.targetDir)
  }

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile("/create_tests/create_tst.py")
  }

  override fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.runAndWait<Throwable> {
        dir.findChild(testsFolderName)?.delete(this)
      }
    }
    super.tearDown()
  }

  private fun getModel(forClass: Boolean): PyTestCreationModel? {
    val pyFile = myFixture.file as PyFile
    val element: PsiElement = if (forClass) pyFile.topLevelClasses[0] else pyFile.findTopLevelFunction("test_foo")!!
    return PyTestCreationModel.createByElement(element)
  }
}
