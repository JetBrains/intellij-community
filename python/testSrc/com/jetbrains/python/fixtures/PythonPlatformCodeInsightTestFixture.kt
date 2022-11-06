// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.fixture.PythonCommonCodeInsightTestFixture
import com.jetbrains.python.psi.LanguageLevel
import junit.framework.TestCase.assertNotNull
import java.io.File

class PythonPlatformCodeInsightTestFixture(languageLevel: LanguageLevel) : PythonCommonCodeInsightTestFixture {
  private val myDelegateTestCase = PyDelegateTestCase(languageLevel)
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
  override val testRootDisposable: Disposable
    get() = myDelegateFixture.testRootDisposable
  override val caretOffset: Int
    get() = myDelegateFixture.caretOffset

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

  override fun setTestDataPath(path: String) {
    myDelegateFixture.testDataPath = path
  }

  override fun configureByFile(filePath: String): PsiFile? = myDelegateFixture.configureByFile(filePath)

  override fun configureByText(fileType: PythonFileType, text: String): PsiFile? = myDelegateFixture.configureByText(fileType, text)

  override fun configureByText(fileName: String, text: String): PsiFile? = myDelegateFixture.configureByText(fileName, text)

  override fun copyDirectoryToProject(sourceFilePath: String, targetPath: String): VirtualFile =
    myDelegateFixture.copyDirectoryToProject(sourceFilePath, targetPath)

  override fun addFileToProject(relativePath: String, fileText: String): PsiFile? =
    myDelegateFixture.addFileToProject(relativePath, fileText)

  override fun configureByFiles(vararg filePaths: String): Array<PsiFile> = myDelegateFixture.configureByFiles(*filePaths)

  override fun configureFromTempProjectFile(filePath: String): PsiFile? = myDelegateFixture.configureFromTempProjectFile(filePath)

  override fun copyFileToProject(sourceFilePath: String): VirtualFile = myDelegateFixture.copyFileToProject(sourceFilePath)

  override fun findFileInTempDir(filePath: String): VirtualFile? = myDelegateFixture.findFileInTempDir(filePath)

  override fun completeBasic(): Array<LookupElement>? = myDelegateFixture.completeBasic()

  override fun completeBasicAllCarets(charToTypeAfterCompletion: Char?): List<LookupElement> =
    myDelegateFixture.completeBasicAllCarets(charToTypeAfterCompletion)

  override fun complete(completionType: CompletionType): Array<LookupElement>? = myDelegateFixture.complete(completionType)

  override fun complete(type: CompletionType, invocationCount: Int): Array<LookupElement>? =
    myDelegateFixture.complete(type, invocationCount)

  override fun checkResultByFile(expectedFile: String) {
    myDelegateFixture.checkResultByFile(expectedFile)
  }

  override fun getLookupElementStrings(): List<String>? = myDelegateFixture.lookupElementStrings

  override fun type(c: Char) {
     myDelegateFixture.type(c)
  }

  override fun type(s: String) {
    myDelegateFixture.type(s)
  }

  override fun checkResult(text: String) {
    myDelegateFixture.checkResult(text)
  }

  override fun finishLookup(completionChar: Char) {
    myDelegateFixture.finishLookup(completionChar)
  }

  override fun addExcludedRoot(rootPath: String) {
    val dir = myDelegateFixture.findFileInTempDir(rootPath)
    assertNotNull(dir)
    PsiTestUtil.addExcludedRoot(module, dir)
    Disposer.register(myDelegateFixture.projectDisposable, Disposable { PsiTestUtil.removeExcludedRoot(module, dir) })
  }

  override fun getLookupElements(): Array<LookupElement>? = myDelegateFixture.lookupElements

  override fun runWithSourceRoots(sourceRoots: List<VirtualFile>, runnable: Runnable) {
    sourceRoots.forEach { root -> PsiTestUtil.addSourceRoot(module, root) }
    try {
      runnable.run()
    }
    finally {
      sourceRoots.forEach { root -> PsiTestUtil.removeSourceRoot(module, root) }
    }
  }
}

class PyDelegateTestCase(private val languageLevel: LanguageLevel = LanguageLevel.getLatest()) : PyTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return if (languageLevel.isPython2) ourPy2Descriptor else super.getProjectDescriptor()
  }

  public override fun addSuppressedException(e: Throwable) {
    super.addSuppressedException(e)
  }

  @Throws(Exception::class)
  fun runTest(test: ThrowableRunnable<Throwable>) {
    runInEdtAndWait {
      runTestRunnable(test)
    }
  }
}
