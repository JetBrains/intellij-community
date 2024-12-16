// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

internal object TerminalOutputTokenTypes {
  val FILE: IFileElementType = object : IFileElementType("TERMINAL_OUTPUT_FILE", TerminalOutputLanguage) {
    override fun parseContents(chameleon: ASTNode): ASTNode? {
      return ASTFactory.leaf(TEXT, chameleon.getChars())
    }
  }

  val TEXT: IElementType = IElementType("TEXT", TerminalOutputLanguage)
}