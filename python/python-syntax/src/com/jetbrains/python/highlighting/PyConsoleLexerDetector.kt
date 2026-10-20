// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.highlighting

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Tells [PySyntaxHighlighterFactoryBase] when a file needs the console lexer instead of the file lexer.
 * The Python console registers a detector. A process without the console has no detector and always uses the file lexer.
 */
@ApiStatus.Internal
interface PyConsoleLexerDetector {
  /**
   * Returns `true` when [file] holds Python console input.
   */
  fun isConsoleFile(project: Project, file: VirtualFile): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyConsoleLexerDetector> = ExtensionPointName("Pythonid.consoleLexerDetector")
  }
}
