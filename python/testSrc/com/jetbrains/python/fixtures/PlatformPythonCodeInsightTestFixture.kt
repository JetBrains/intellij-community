// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.fixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.fixture.CommonPythonCodeInsightTestFixture
import java.io.File
import java.lang.reflect.InvocationTargetException

class PlatformPythonCodeInsightTestFixture : CommonPythonCodeInsightTestFixture {
  private val myDelegateTestCase = PyDelegateTestCase()
  private val myDelegateFixture: CodeInsightTestFixture
    get() = myDelegateTestCase.myFixture

  override val project: Project
    get() = myDelegateFixture.project
  override val module: Module
    get() = myDelegateFixture.module
  override val file: PsiFile?
    get() = myDelegateFixture.file
  override val testDataRoot: VirtualFile?
    get() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(PythonTestUtil.getTestDataPath()))
  override val psiManager: PsiManager
    get() = myDelegateFixture.psiManager
  override val tempDirRoot: VirtualFile
    get() = requireNotNull(myDelegateFixture.tempDirFixture.getFile("."))

  override fun setUp() {
    super.setUp()
    myDelegateTestCase.setUp()
  }

  override fun tearDown() {
    myDelegateTestCase.tearDown()
    super.tearDown()
  }

  @Throws(Exception::class)
  override fun runTest(test: ThrowableRunnable<Throwable>) {
    myDelegateTestCase.runTest(test)
  }

  override fun addSuppressedException(e: Throwable) {
    myDelegateTestCase.addSuppressedException(e)
  }

  override fun configureByFile(filePath: String): PsiFile? = myDelegateFixture.configureByFile(filePath)

  override fun configureByText(fileType: PythonFileType, text: String): PsiFile? = myDelegateFixture.configureByText(fileType, text)

  override fun configureByText(fileName: String, text: String): PsiFile? = myDelegateFixture.configureByText(fileName, text)

  override fun copyDirectoryToProject(sourceFilePath: String, targetPath: String): VirtualFile? =
    myDelegateFixture.copyDirectoryToProject(sourceFilePath, targetPath)

  override fun addFileToProject(relativePath: String, fileText: String): PsiFile? = myDelegateFixture.addFileToProject(relativePath,
                                                                                                                       fileText)
}

class PyDelegateTestCase : PyTestCase() {

  public override fun addSuppressedException(e: Throwable) {
    super.addSuppressedException(e)
  }

  @Throws(Exception::class)
  fun runTest(test: ThrowableRunnable<Throwable>) {
    val throwables = arrayOfNulls<Throwable>(1)
    invokeTestRunnable {
      try {
        TestLoggerFactory.onTestStarted()
        test.run()
        TestLoggerFactory.onTestFinished(true)
      }
      catch (e: InvocationTargetException) {
        TestLoggerFactory.onTestFinished(false)
        e.fillInStackTrace()
        throwables[0] = e.targetException
      }
      catch (e: IllegalAccessException) {
        TestLoggerFactory.onTestFinished(false)
        e.fillInStackTrace()
        throwables[0] = e
      }
      catch (e: Throwable) {
        TestLoggerFactory.onTestFinished(false)
        throwables[0] = e
      }
    }

    val throwable = throwables[0]
    throwable?.let { throw it }
  }
}