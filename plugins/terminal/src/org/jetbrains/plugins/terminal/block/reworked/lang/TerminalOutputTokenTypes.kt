// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

internal object TerminalOutputTokenTypes {
  val FILE: IFileElementType = IFileElementType("TERMINAL_OUTPUT_FILE", TerminalOutputLanguage)
  val TEXT: IElementType = IElementType("TEXT", TerminalOutputLanguage)
}