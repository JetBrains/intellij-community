// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the kernel language of a notebook file without forcing python-lsp-core to
 * depend on any concrete notebook framework. Integration modules register implementations.
 */
@ApiStatus.Internal
interface NotebookLanguageResolver {
  /**
   * Returns the kernel language of [file] if this resolver recognises it as a
   * notebook, or `null` otherwise.
   */
  fun resolveLanguage(file: VirtualFile): Language?

  companion object {
    val EP_NAME: ExtensionPointName<NotebookLanguageResolver> =
      ExtensionPointName.create("com.intellij.python.lsp.notebookLanguageResolver")
  }
}
