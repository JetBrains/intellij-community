// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.black

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Writes [content] to `<tempDir>/<fileName>`, refreshes VFS, and returns the resolved [PsiFile].
 * Use a unique [fileName] per test to avoid stale `VirtualFile` / `Document` state when a
 * companion-scoped project is reused across tests.
 */
internal fun createPsiFileOnDisk(
  project: Project,
  tempDir: Path,
  fileName: String,
  content: String,
): PsiFile {
  val nioFile = tempDir.resolve(fileName).also { it.writeText(content) }
  return timeoutRunBlocking {
    val vFile: VirtualFile = withContext(Dispatchers.EDT) {
      edtWriteAction {
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioFile)) {
          "Failed to refresh VFS for $nioFile"
        }
      }
    }
    readAction {
      checkNotNull(PsiManager.getInstance(project).findFile(vFile)) { "No PsiFile for $nioFile" }
    }
  }
}

/** Runs `CodeStyleManager.reformat` on [psiFile] inside a write command + commits documents. */
internal fun reformatPsiFile(project: Project, psiFile: PsiFile) {
  WriteCommandAction.runWriteCommandAction(project) {
    CodeStyleManager.getInstance(project).reformat(psiFile)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }
}

/** Runs `CodeStyleManager.reformatRange(psiFile, start, end)` inside a write command + commits. */
internal fun reformatPsiFileRange(project: Project, psiFile: PsiFile, startOffset: Int, endOffset: Int) {
  WriteCommandAction.runWriteCommandAction(project) {
    CodeStyleManager.getInstance(project).reformatRange(psiFile, startOffset, endOffset)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }
}

