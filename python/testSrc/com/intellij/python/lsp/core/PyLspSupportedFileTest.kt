// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.lang.Language
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.fixtures.PyTestCase

class PyLspSupportedFileTest : PyTestCase() {
  fun testPythonSourceIsSupported() {
    val pythonFile = LightVirtualFile("test.py", PythonFileType.INSTANCE, "x = 1")

    assertTrue(isPythonFile(pythonFile))
  }

  fun testNotebookIsNotSupportedWhenGlobalNotebookLspIsDisabled() {
    val notebook = LightVirtualFile("test.ipynb")

    assertFalse(
      isPythonFile(notebook, notebookSupported = true, resolvers = listOf(FakeNotebookLanguageResolver(PythonLanguageHolder.language))),
    )
  }

  fun testNotebookIsNotSupportedWhenDescriptorDoesNotSupportNotebooks() {
    val notebook = LightVirtualFile("test.ipynb")

    assertFalse(
      isPythonFile(notebook, notebookSupported = false, resolvers = listOf(FakeNotebookLanguageResolver(PythonLanguageHolder.language))),
    )
  }

  private class FakeNotebookLanguageResolver(private val language: Language?) : NotebookLanguageResolver {
    override fun resolveLanguage(file: com.intellij.openapi.vfs.VirtualFile): Language? = language
  }

  private object PythonLanguageHolder {
    val language: Language = PythonLanguage.INSTANCE
  }
}
