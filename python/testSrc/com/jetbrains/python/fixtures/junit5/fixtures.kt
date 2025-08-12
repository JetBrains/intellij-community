// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures.junit5

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path


@TestOnly
fun TestFixture<PsiDirectory>.psiFileFixture(fileRelativePath: Path): TestFixture<PsiFile> = testFixture { _ ->
  val sourceRootDirectory = this@psiFileFixture.init()
  val virtualFile = sourceRootDirectory.virtualFile.resolveFromRootOrRelative(fileRelativePath.toString())
                    ?: error("Can't resolve VirtualFile for $fileRelativePath")
  val psiFile = readAction {
    sourceRootDirectory.manager.findFile(virtualFile)
    ?: error("Can't find PsiFile for $virtualFile")
  }
  initialized(psiFile) {}
}