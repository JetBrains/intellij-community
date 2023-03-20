package com.jetbrains.python.fixture

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.PythonFileType

interface PythonCommonCodeInsightTestFixture {
  val tempDirRoot: VirtualFile

  val project: Project

  val module: Module

  val file: PsiFile?

  val testDataRoot: VirtualFile?

  val psiManager: PsiManager

  val testRootDisposable: Disposable

  val caretOffset: Int

  fun setUp() {
  }

  fun tearDown() {
  }

  fun setTestDataPath(path: String)

  @Throws(Exception::class)
  fun runTest(test: ThrowableRunnable<Throwable>) {
    test.run()
  }

  fun addSuppressedException(e: Throwable) {
  }

  fun configureByFile(filePath: String): PsiFile?

  fun configureByFiles(vararg filePaths: String): Array<PsiFile>

  fun configureByText(fileType: PythonFileType, text: String): PsiFile?

  fun configureByText(fileName: String, text: String): PsiFile?

  fun configureFromTempProjectFile(filePath: String): PsiFile?

  fun copyFileToProject(sourceFilePath: String): VirtualFile?

  fun copyDirectoryToProject(sourceFilePath: String, targetPath: String): VirtualFile?

  fun findFileInTempDir(filePath: String): VirtualFile?

  fun addFileToProject(relativePath: String, fileText: String): PsiFile?

  fun completeBasic(): Array<LookupElement>?

  fun completeBasicAllCarets(charToTypeAfterCompletion: Char?): List<LookupElement>

  fun complete(completionType: CompletionType): Array<LookupElement>?

  fun complete(type: CompletionType, invocationCount: Int): Array<LookupElement>?

  fun checkResultByFile(expectedFile: String)

  fun getLookupElementStrings(): List<String>?

  fun type(c: Char)

  fun type(s: String)

  fun checkResult(text: String)

  fun finishLookup(completionChar: Char)

  fun addExcludedRoot(rootPath: String)

  fun getLookupElements(): Array<LookupElement>?

  // TODO: this belongs to test case not fixture [utikeev]
  fun runWithSourceRoots(sourceRoots: List<VirtualFile>, runnable: Runnable)
}
