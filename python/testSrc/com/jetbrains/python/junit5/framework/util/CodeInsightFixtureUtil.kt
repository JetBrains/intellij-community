// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.util

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.IntentionalUnstubbing
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil
import org.junit.jupiter.api.Assertions

private val LOG = logger<CodeInsightTestFixture>()

fun CodeInsightTestFixture.doTestByFile(file: PsiFile) {
  this.configureFromExistingVirtualFile(file.virtualFile)
  this.doHighlighting()
  this.checkHighlighting(true, false, true)
  this.assertSdkRootsNotParsed(file)
}

fun CodeInsightTestFixture.doTestByText(text: String) {
  this.configureByText(PythonFileType.INSTANCE, text)
  this.doHighlighting()
  this.checkHighlighting(true, false, true)
  this.assertSdkRootsNotParsed(this.file)
}

/**
 * Asserts that the given [file] has not been parsed to AST (i.e., operations used stubs).
 *
 * Files that were intentionally unstubbed via [IntentionalUnstubbing.onFileOf] are excluded from the check.
 */
fun assertNotParsed(file: PsiFile) {
  if (IntentionalUnstubbing.getForciblyUnstubbedFiles().contains(file)) return
  Assertions.assertInstanceOf(PyFileImpl::class.java, file)
  val virtualFile = file.virtualFile
  val errorMessage = "Operations should have been performed on stubs but caused file to be parsed: ${virtualFile.path}"
  val tip = "As a starting point for an investigation, a breakpoint can be set in " +
            "com.intellij.psi.impl.source.PsiFileImpl#loadTreeElement with a condition " +
            "`getName().equals(\"${virtualFile.name}\")`. Then the stacktrace can be investigated to find the root cause."
  Assertions.assertNull((file as PyFileImpl).treeElement, "$errorMessage\n$tip")
}

fun CodeInsightTestFixture.assertSdkRootsNotParsed(currentFile: PsiFile) {
  val testSdk = runReadActionBlocking { PythonSdkUtil.findPythonSdk(currentFile) }
  if (testSdk == null) {
    LOG.warn("testSdk is null. assertSdkRootsNotParsed is skipped")
    return
  }
  for (root in testSdk.rootProvider.getFiles(OrderRootType.CLASSES)) {
    assertRootNotParsed(currentFile, root, null)
  }
}

fun CodeInsightTestFixture.assertProjectFilesNotParsed(currentFile: PsiFile) {
  assertRootNotParsed(currentFile, tempDirFixture.getFile(".")!!, null)
}

fun CodeInsightTestFixture.assertProjectFilesNotParsed(context: TypeEvalContext) {
  val origin = context.origin ?: return
  assertRootNotParsed(origin, tempDirFixture.getFile(".")!!, context)
}

private fun CodeInsightTestFixture.assertRootNotParsed(
  currentFile: PsiFile,
  root: VirtualFile,
  context: TypeEvalContext?,
) {
  runReadActionBlocking {
    for (file in VfsUtil.collectChildrenRecursively(root)) {
      val pyFile = psiManager.findFile(file) as? PyFile ?: continue
      if (pyFile == currentFile) continue
      if (context != null && context.maySwitchToAST(pyFile)) continue
      assertNotParsed(pyFile)
    }
  }
}