// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.testIntegration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.VfsTestUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import com.jetbrains.python.testing.TestRunnerService

class PyTestCreationModelTest : PyTestCase() {
  private val dir get() = myFixture.file.containingDirectory.virtualFile
  private val dirPath get() = dir.path
  private val service: TestRunnerService get() = TestRunnerService.getInstance(myFixture.module)
  private val testsFolderName = "tests"

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile("/create_tests/create_tst.py")
  }

  fun testWithUnitTest() {
    service.projectConfiguration = PythonTestConfigurationType.getInstance().unitTestFactory.name
    val modelToTestClass = getModel()!!
    assertEquals("test_create_tst.py", modelToTestClass.fileName)
    assertEquals("TestSpam", modelToTestClass.className)
    assertEquals(dirPath, modelToTestClass.targetDir)
    assertEquals(modelToTestClass.methods, listOf("test_eggs", "test_eggs_and_ham"))

    val modelToTestFunction = getModelForFunc()!!
    assertEquals("test_create_tst.py", modelToTestFunction.fileName)
    assertEquals("Test", modelToTestFunction.className)
    assertEquals(dirPath, modelToTestClass.targetDir)
    assertEquals(modelToTestFunction.methods, listOf("test_test_foo"))

    val modelToTestEmptyClass = getModel("SpamSpamSpamBakedBeans")!!
    assertEquals("test_create_tst.py", modelToTestEmptyClass.fileName)
    assertEquals("TestSpamSpamSpamBakedBeans", modelToTestEmptyClass.className)
    assertEquals(dirPath, modelToTestEmptyClass.targetDir)
    assertEquals(modelToTestEmptyClass.methods, emptyList<String>())
  }

  fun testWithPyTest() {
    service.projectConfiguration = PyTestFactory(PythonTestConfigurationType.getInstance()).id
    val modelToTestClass = getModel()!!
    assertEquals("test_create_tst.py", modelToTestClass.fileName)
    assertEquals("", modelToTestClass.className)
    assertEquals(dirPath, modelToTestClass.targetDir)
    assertEquals(modelToTestClass.methods, listOf("test_eggs", "test_eggs_and_ham"))

    assertNull("test_foo is test from pytest point of view, can't test it", getModelForFunc())

    val modelToTestEmptyClass = getModel("SpamSpamSpamBakedBeans")!!
    assertEquals("test_create_tst.py", modelToTestEmptyClass.fileName)
    assertEquals("", modelToTestEmptyClass.className)
    assertEquals(dirPath, modelToTestEmptyClass.targetDir)
    assertEquals(modelToTestEmptyClass.methods, listOf("test_spam_spam_spam_baked_beans"))
  }

  fun testTestFolderDetected() {
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.runAndWait<Throwable> {
        VfsTestUtil.createDir(dir, testsFolderName)
      }
    }
    val modelToTestClass = getModel()!!
    assertEquals(dir.findChild(testsFolderName)!!.path, modelToTestClass.targetDir)
  }

  override fun tearDown() {
    try {
      ApplicationManager.getApplication().invokeAndWait {
        WriteAction.runAndWait<Throwable> {
          dir.findChild(testsFolderName)?.let { VfsTestUtil.deleteFile(it) }
        }
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun getModelForFunc() = getModel(null)
  private fun getModel(forClass: String? = "Spam"): PyTestCreationModel? {
    val pyFile = myFixture.file as PyFile
    val element: PsiElement = when {
      forClass != null -> pyFile.findTopLevelClass(forClass)!!
      else -> pyFile.findTopLevelFunction("test_foo")!!
    }
    return PyTestCreationModel.createByElement(element)
  }
}
