package com.jetbrains.python.fixture

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.PythonFileType

interface CommonPythonCodeInsightTestFixture {
  val tempDirRoot: VirtualFile

  val project: Project

  val module: Module

  val file: PsiFile?

  val testDataRoot: VirtualFile?

  val psiManager: PsiManager

  fun setUp() {
  }

  fun tearDown() {
  }

  @Throws(Exception::class)
  fun runTest(test: ThrowableRunnable<Throwable>) {
    test.run()
  }

  fun addSuppressedException(e: Throwable) {
  }

  fun configureByFile(filePath: String): PsiFile?

  fun configureByText(fileType: PythonFileType, text: String): PsiFile?

  fun configureByText(fileName: String, text: String): PsiFile?

  fun copyDirectoryToProject(sourceFilePath: String, targetPath: String): VirtualFile?

  fun addFileToProject(relativePath: String, fileText: String): PsiFile?
}