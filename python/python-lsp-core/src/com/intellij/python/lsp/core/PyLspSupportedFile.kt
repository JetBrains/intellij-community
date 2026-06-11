// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.pyi.PyiFileType
import org.jetbrains.annotations.ApiStatus

/**
 * Predicate shared by [PyLspToolDescriptor.isSupportedFile] and
 * [PyLspToolIntegrationProvider.fileOpened].
 *
 * Accepts:
 *  - regular Python sources ([PythonFileType])
 *  - Python stub files ([PyiFileType])
 *  - when [notebookSupported] is `true`, notebooks whose kernel language resolves
 *      to Python via [NotebookLanguageResolver].
 *
 * [notebookSupported] is a per-descriptor knob: a python LSP tool that can serve
 * notebooks opts in by overriding its `supportsNotebooks` property to `true`.
 */
@ApiStatus.Internal
fun isPythonFile(file: VirtualFile, notebookSupported: Boolean = false): Boolean =
  isPythonFile(
    file,
    notebookSupported,
    NotebookLanguageResolver.EP_NAME.extensionList,
  )

/**
 * Test helper: pass explicit [NotebookLanguageResolver] implementations instead of relying on the EP.
 */
@ApiStatus.Internal
fun isPythonFile(
  file: VirtualFile,
  notebookSupported: Boolean,
  resolvers: List<NotebookLanguageResolver>,
): Boolean {
  if (isPythonSourceFile(file)) return true
  if (!notebookSupported) return false
  return isPythonNotebookLanguage(file, resolvers)
}

@ApiStatus.Internal
fun isPythonSourceFile(file: VirtualFile): Boolean =
  file.fileType is PythonFileType || file.fileType is PyiFileType

@ApiStatus.Internal
fun isPythonNotebookLanguage(file: VirtualFile, resolvers: List<NotebookLanguageResolver>): Boolean =
  resolvers.any { it.resolveLanguage(file)?.isKindOf(PythonLanguage.INSTANCE) == true }
